package com.resumepipeline.api;

import com.resumepipeline.api.dto.ProjectDtos.ProjectResponse;
import com.resumepipeline.auth.AuthUtils;
import com.resumepipeline.github.GithubClient;
import com.resumepipeline.github.GithubException;
import com.resumepipeline.api.dto.ApplicationDtos.SubmitResponse;
import com.resumepipeline.github.GithubService;
import com.resumepipeline.github.RepoExplorer;
import com.resumepipeline.github.RepoMap;
import com.resumepipeline.github.RepoMapRenderer;
import com.resumepipeline.github.RepoReader;
import com.resumepipeline.github.RepoSnapshot;
import com.resumepipeline.github.SourceTracer;
import com.resumepipeline.llm.LlmUsageService;
import com.resumepipeline.llm.TokenAccumulator;
import com.resumepipeline.obs.Mdc;
import com.resumepipeline.progress.ProgressLog;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/github")
public class GithubController {

    private static final Logger log = LoggerFactory.getLogger(GithubController.class);
    private static final String STATE_ATTR = "github.oauth.state";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final ExecutorService ASYNC_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final GithubService github;
    /** Where the browser lands after connecting: the frontend's origin on a split deploy, "" (same origin) otherwise. */
    private final String returnOrigin;
    private final RepoExplorer explorer;
    private final JobProgressStore jobStore;
    private final LlmUsageService usage;

    public GithubController(GithubService github, RepoExplorer explorer, JobProgressStore jobStore, LlmUsageService usage,
                            @Value("${github.return-origin:}") String returnOrigin) {
        this.github = github;
        this.returnOrigin = returnOrigin.replaceAll("/+$", "");
        this.explorer = explorer;
        this.jobStore = jobStore;
        this.usage = usage;
    }

    @GetMapping("/status")
    public GithubService.Status status(Authentication auth) {
        return github.status(AuthUtils.userId(auth));
    }

    /** Browser navigates here; we bind a one-time state to the session and bounce to GitHub's OAuth page. */
    @GetMapping("/connect")
    public ResponseEntity<Void> connect(HttpSession session) {
        return redirect(github.authorizeUrl(newState(session)));
    }

    /**
     * GitHub's Callback URL, reached two ways: from OAuth ({@code code} + our {@code state}),
     * or straight after a fresh install ({@code code} + {@code installation_id}, and possibly no
     * {@code state}). The install hop is not trusted on its own: it restarts OAuth, which the
     * user already approved, so it bounces straight back here with a state we can check.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(Authentication auth, HttpSession session,
                                         @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(name = "installation_id", required = false) Long installationId) {
        Object expected = session.getAttribute(STATE_ATTR);
        session.removeAttribute(STATE_ATTR);
        if (code == null) {
            return redirect(returnOrigin + "/settings?github=error");
        }
        if (state == null || !state.equals(expected)) {
            if (state == null && installationId != null) return redirect("/api/github/connect");
            return redirect(returnOrigin + "/settings?github=error");
        }
        try {
            github.connect(AuthUtils.userId(auth), code, installationId);
            return redirect(returnOrigin + "/settings?github=connected");
        } catch (GithubException.NoInstallation e) {
            // Authorized but not installed yet: send them to install, then back here.
            return redirect(github.installUrl(newState(session)));
        } catch (RuntimeException e) {
            log.warn("GITHUB_CONNECT_FAILED cause={}", e.getMessage());
            return redirect(returnOrigin + "/settings?github=error");
        }
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(Authentication auth) {
        github.disconnect(AuthUtils.userId(auth));
    }

    @GetMapping("/repos")
    public List<GithubClient.Repo> repos(Authentication auth) {
        return github.repos(AuthUtils.userId(auth));
    }

    /** projectId null creates a new project for the repo. */
    public record LinkRequest(UUID projectId, String fullName, String branch) {}

    @PostMapping("/link")
    public ProjectResponse link(Authentication auth, @RequestBody LinkRequest req) {
        if (req.fullName() == null || req.fullName().isBlank()) throw new GithubException("fullName is required");
        return ProjectResponse.from(github.link(AuthUtils.userId(auth), req.projectId(), req.fullName(), req.branch()));
    }

    @GetMapping("/projects/{id}/tree")
    public GithubClient.Tree tree(Authentication auth, @PathVariable UUID id) {
        return github.tree(AuthUtils.userId(auth), id);
    }

    @GetMapping(value = "/projects/{id}/file", produces = MediaType.TEXT_PLAIN_VALUE)
    public String file(Authentication auth, @PathVariable UUID id, @RequestParam String path) {
        return github.file(AuthUtils.userId(auth), id, path);
    }

    /** The hierarchical map for the project's pinned commit; 204 until the first exploration builds it. */
    @GetMapping("/projects/{id}/map")
    public ResponseEntity<RepoMap> map(Authentication auth, @PathVariable UUID id) {
        RepoMap m = github.map(AuthUtils.userId(auth), id);
        return m == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(m);
    }

    @GetMapping("/projects/{id}/bullet-sources")
    public Map<UUID, List<SourceTracer.Source>> bulletSources(Authentication auth, @PathVariable UUID id) {
        return github.bulletSources(AuthUtils.userId(auth), id);
    }

    /**
     * Runs the repo explorer and fills the project's context from what it verified. Poll
     * {@code /api/projects/jobs/{jobId}/progress}; on DONE the result id is the project id.
     */
    @PostMapping("/projects/{id}/explore/submit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubmitResponse explore(Authentication auth, @PathVariable UUID id, @RequestBody RepoExplorer.Steering steering,
                                  @RequestParam(defaultValue = "false") boolean rebuildMap) {
        UUID userId = AuthUtils.userId(auth);
        RepoReader reader = github.reader(userId, id); // fail fast: not linked / not connected
        UUID jobId = UUID.randomUUID();
        jobStore.start(jobId, userId);
        ASYNC_EXECUTOR.submit(Mdc.wrap(() -> {
            ProgressLog progress = msg -> jobStore.append(jobId, msg);
            TokenAccumulator tokens = new TokenAccumulator();
            try {
                progress.emit("Downloading " + reader.repo() + " @ " + reader.sha().substring(0, 7) + "...");
                RepoSnapshot snap = github.snapshot(userId, reader);
                RepoMap map = github.ensureMap(userId, id, snap, rebuildMap, progress, tokens);
                progress.emit("Exploring with the map...");
                RepoExplorer.Outcome out = explorer.explore(reader.withSnapshot(snap), steering,
                        RepoMapRenderer.forExplorer(map), progress, tokens);
                for (String d : out.droppedClaims()) progress.emit("cut: " + d);
                github.applyExplore(userId, id, out, map);
                progress.emit("saved context from " + out.steps() + " steps, " + out.evidence().size() + " verified citations");
                jobStore.complete(jobId, id);
            } catch (Exception e) {
                log.error("EXPLORE_FAILED job={} cause={}", jobId, e.getMessage(), e);
                jobStore.fail(jobId, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            } finally {
                usage.record(userId, "repo_explore", tokens, null, id);
            }
        }));
        return new SubmitResponse(jobId);
    }

    private static String newState(HttpSession session) {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        String state = HexFormat.of().formatHex(b);
        session.setAttribute(STATE_ATTR, state);
        return state;
    }

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, URI.create(location).toString()).build();
    }
}
