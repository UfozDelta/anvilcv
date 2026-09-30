package com.resumepipeline.api;

import com.resumepipeline.api.dto.ProjectDtos.ProjectResponse;
import com.resumepipeline.auth.AuthUtils;
import com.resumepipeline.github.GithubClient;
import com.resumepipeline.github.GithubException;
import com.resumepipeline.api.dto.ApplicationDtos.SubmitResponse;
import com.resumepipeline.github.GithubService;
import com.resumepipeline.github.RepoExplorer;
import com.resumepipeline.github.RepoReader;
import com.resumepipeline.llm.LlmUsageService;
import com.resumepipeline.llm.TokenAccumulator;
import com.resumepipeline.obs.Mdc;
import com.resumepipeline.progress.ProgressLog;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final RepoExplorer explorer;
    private final JobProgressStore jobStore;
    private final LlmUsageService usage;

    public GithubController(GithubService github, RepoExplorer explorer, JobProgressStore jobStore, LlmUsageService usage) {
        this.github = github;
        this.explorer = explorer;
        this.jobStore = jobStore;
        this.usage = usage;
    }

    @GetMapping("/status")
    public GithubService.Status status(Authentication auth) {
        return github.status(AuthUtils.userId(auth));
    }

    /** Browser navigates here; we bind a one-time state to the session and bounce to GitHub. */
    @GetMapping("/connect")
    public ResponseEntity<Void> connect(HttpSession session) {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        String state = HexFormat.of().formatHex(b);
        session.setAttribute(STATE_ATTR, state);
        return redirect(github.installUrl(state));
    }

    /** GitHub's "Callback URL" (OAuth during install). Always lands the browser back on Settings. */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(Authentication auth, HttpSession session,
                                         @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(name = "installation_id", required = false) Long installationId) {
        Object expected = session.getAttribute(STATE_ATTR);
        session.removeAttribute(STATE_ATTR);
        if (code == null || state == null || !state.equals(expected)) {
            return redirect("/settings?github=error");
        }
        try {
            github.connect(AuthUtils.userId(auth), code, installationId);
            return redirect("/settings?github=connected");
        } catch (RuntimeException e) {
            log.warn("GITHUB_CONNECT_FAILED cause={}", e.getMessage());
            return redirect("/settings?github=error");
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

    /**
     * Runs the repo explorer and fills the project's context from what it verified. Poll
     * {@code /api/projects/jobs/{jobId}/progress}; on DONE the result id is the project id.
     */
    @PostMapping("/projects/{id}/explore/submit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubmitResponse explore(Authentication auth, @PathVariable UUID id, @RequestBody RepoExplorer.Steering steering) {
        UUID userId = AuthUtils.userId(auth);
        RepoReader reader = github.reader(userId, id); // fail fast: not linked / not connected
        UUID jobId = UUID.randomUUID();
        jobStore.start(jobId, userId);
        ASYNC_EXECUTOR.submit(Mdc.wrap(() -> {
            ProgressLog progress = msg -> jobStore.append(jobId, msg);
            TokenAccumulator tokens = new TokenAccumulator();
            try {
                progress.emit("Exploring " + reader.repo() + " @ " + reader.sha().substring(0, 7) + "...");
                RepoExplorer.Outcome out = explorer.explore(reader, steering, progress, tokens);
                for (String d : out.droppedClaims()) progress.emit("cut: " + d);
                github.applyExplore(userId, id, out);
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

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, URI.create(location).toString()).build();
    }
}
