package com.resumepipeline.github;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumepipeline.bullet.Bullet;
import com.resumepipeline.bullet.BulletRepository;
import com.resumepipeline.llm.LlmClient;
import com.resumepipeline.llm.TokenAccumulator;
import com.resumepipeline.progress.ProgressLog;
import com.resumepipeline.project.Project;
import com.resumepipeline.project.ProjectRepository;
import com.resumepipeline.project.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongFunction;

/**
 * Per-user GitHub link and the repo reads behind the project's side-loaded repo view. A
 * project's reads are pinned to the commit it was linked at, so the tree, the files, and the
 * explorer's evidence all describe the same snapshot.
 */
@Service
public class GithubService {

    private static final Logger log = LoggerFactory.getLogger(GithubService.class);

    /** Files larger than this are refused outright in the viewer and by the explorer. */
    public static final int MAX_FILE_BYTES = 200_000;

    /** Same ceiling GithubContextFetcher puts on repo context sent to generation. */
    private static final int MAX_REPO_CONTEXT_CHARS = 30_000;
    private static final ObjectMapper JSON = new ObjectMapper();

    public record Status(boolean configured, boolean connected, String account, String manageUrl) {}

    private final GithubAppAuth auth;
    private final GithubClient client;
    private final GithubInstallationRepository installs;
    private final ProjectService projects;
    private final ProjectRepository projectRepo;
    private final BulletRepository bullets;
    private final RepoMapService mapper;
    private final String webBase;

    public GithubService(GithubAppAuth auth, GithubClient client, GithubInstallationRepository installs,
                         ProjectService projects, ProjectRepository projectRepo, BulletRepository bullets,
                         RepoMapService mapper,
                         @Value("${github.web-base:https://github.com}") String webBase) {
        this.bullets = bullets;
        this.mapper = mapper;
        this.auth = auth;
        this.client = client;
        this.installs = installs;
        this.projects = projects;
        this.projectRepo = projectRepo;
        this.webBase = webBase;
    }

    public Status status(UUID userId) {
        Optional<GithubInstallation> i = installs.findById(userId);
        return new Status(auth.isConfigured(), i.isPresent(), i.map(GithubInstallation::getAccountLogin).orElse(null),
                i.map(x -> webBase + "/settings/installations/" + x.getInstallationId()).orElse(null));
    }

    /**
     * Where Connect starts: plain OAuth for the app. Unlike the install page it always returns
     * {@code state}, and it works whether or not the app is already installed — once installed,
     * the install page turns into a "Configure" screen that never redirects back.
     */
    public String authorizeUrl(String state) {
        requireConfigured();
        return webBase + "/login/oauth/authorize?client_id="
                + java.net.URLEncoder.encode(auth.clientId(), java.nio.charset.StandardCharsets.UTF_8) + "&state=" + state;
    }

    /** Install page, used only when the authorized user has no installation yet. */
    public String installUrl(String state) {
        requireConfigured();
        return webBase + "/apps/" + auth.slug() + "/installations/new?state=" + state;
    }

    /**
     * Links the installation to the user. The callback's {@code installation_id} query param is
     * attacker-controllable, so it is only accepted if it appears in the installations GitHub
     * lists for the token the {@code code} exchanges into. The user token is then dropped.
     */
    public Status connect(UUID userId, String code, Long installationIdParam) {
        requireConfigured();
        String userToken = client.exchangeCode(code);
        List<GithubClient.Installation> mine = client.userInstallations(userToken);
        GithubClient.Installation pick = mine.stream()
                .filter(i -> installationIdParam == null || i.id() == installationIdParam)
                .findFirst()
                .orElseThrow(GithubException.NoInstallation::new);
        GithubInstallation row = installs.findById(userId).orElse(new GithubInstallation(userId, pick.id(), pick.accountLogin()));
        row.setInstallationId(pick.id());
        row.setAccountLogin(pick.accountLogin());
        installs.save(row);
        log.info("GITHUB_CONNECT user={} installation={}", userId, pick.id());
        return status(userId);
    }

    /** Unlinks locally. Uninstalling the app itself happens on GitHub — see {@link Status#manageUrl()}. */
    public void disconnect(UUID userId) {
        installs.findById(userId).ifPresent(i -> {
            client.forget(i.getInstallationId());
            installs.delete(i);
        });
    }

    public List<GithubClient.Repo> repos(UUID userId) {
        return withInstallation(userId, client::listRepos);
    }

    /** Creates a project for the repo, or re-points an existing one, pinned to the branch head. */
    public Project link(UUID userId, UUID projectId, String fullName, String branch) {
        GithubClient.Repo repo = repos(userId).stream()
                .filter(r -> r.fullName().equalsIgnoreCase(fullName))
                .findFirst()
                .orElseThrow(() -> new GithubException("Repository not granted to the AnvilCV app: " + fullName));
        String ref = branch == null || branch.isBlank() ? repo.defaultBranch() : branch;
        String sha = withInstallation(userId, i -> client.branchHead(i, repo.fullName(), ref));

        Project p = projectId != null
                ? projects.get(userId, projectId)
                : projects.create(userId, Project.Kind.PROJECT, repo.fullName().substring(repo.fullName().indexOf('/') + 1),
                        repo.description().isBlank() ? "Imported from GitHub" : repo.description(),
                        null, null, null, null, null);
        p.setGithubUrl("https://github.com/" + repo.fullName());
        p.setRepoBranch(ref);
        p.setRepoCommitSha(sha);
        return projectRepo.save(p);
    }

    public GithubClient.Tree tree(UUID userId, UUID projectId) {
        Project p = linked(userId, projectId);
        GithubClient.Tree t = withInstallation(userId, i -> client.tree(i, repoName(p), p.getRepoCommitSha()));
        return new GithubClient.Tree(t.entries().stream().filter(e -> !RepoFilter.isNoise(e.path())).toList(), t.truncated());
    }

    public String file(UUID userId, UUID projectId, String path) {
        Project p = linked(userId, projectId);
        if (RepoFilter.isNoise(path)) throw new GithubException("Skipped: vendored, lockfile, or binary path");
        String body = withInstallation(userId, i -> client.readFile(i, repoName(p), path, p.getRepoCommitSha()));
        if (body != null && body.length() > MAX_FILE_BYTES) throw new GithubException("File too large to show: " + path);
        return body;
    }

    /** Repo-scoped read access for the explorer, bound to one user's installation and project snapshot. */
    public RepoReader reader(UUID userId, UUID projectId) {
        Project p = linked(userId, projectId);
        long inst = installation(userId);
        return new RepoReader(client, inst, repoName(p), p.getRepoCommitSha());
    }

    /**
     * Writes a verified explorer outcome onto the project. Only non-blank fields overwrite, so
     * a field the explorer could not support keeps whatever the user typed. The verbatim
     * evidence becomes {@code repoContext}, which bullet generation already reads as grounding.
     */
    public Project applyExplore(UUID userId, UUID projectId, RepoExplorer.Outcome outcome) {
        return applyExplore(userId, projectId, outcome, null);
    }

    /**
     * As above, with the repo map's overview (project, subsystems, flows, counted facts) placed
     * ahead of the evidence in {@code repoContext} — the top of the hierarchy every lens shares.
     */
    public Project applyExplore(UUID userId, UUID projectId, RepoExplorer.Outcome outcome, RepoMap map) {
        Project p = linked(userId, projectId);
        LlmClient.ExtractResult r = outcome.result();
        set(r.techStack(), p::setTechStack);
        set(r.description(), p::setContextDescription);
        set(r.yourRole(), p::setYourRole);
        set(r.ownership(), p::setOwnership);
        set(r.scaleImpact(), p::setScaleImpact);
        set(r.hardestProblem(), p::setHardestProblem);
        set(r.technicalDecisions(), p::setTechnicalDecisions);
        set(r.userImpact(), p::setUserImpact);
        set(r.securityPosture(), p::setSecurityPosture);
        String overview = map == null ? "" : RepoMapRenderer.overview(map) + "\n";
        String evidence = renderEvidence(repoName(p), p.getRepoCommitSha(), outcome.evidence(), MAX_REPO_CONTEXT_CHARS - overview.length());
        String context = overview + (evidence == null ? "" : evidence);
        p.setRepoContext(context.isBlank() ? null : context);
        try {
            p.setRepoEvidence(JSON.writeValueAsString(outcome.evidence()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize evidence", e);
        }
        return projectRepo.save(p);
    }

    public List<RepoExplorer.Evidence> evidence(Project p) {
        if (p.getRepoEvidence() == null || p.getRepoEvidence().isBlank()) return List.of();
        try {
            return JSON.readValue(p.getRepoEvidence(), new TypeReference<List<RepoExplorer.Evidence>>() {});
        } catch (Exception e) {
            log.warn("Unreadable repo_evidence on project {}: {}", p.getId(), e.getMessage());
            return List.of();
        }
    }

    /**
     * The project's map for its pinned commit, rebuilt from {@code snap} only when missing or
     * stale. {@code force} rebuilds anyway (e.g. after the user wants fresher summaries).
     */
    public RepoMap ensureMap(UUID userId, UUID projectId, RepoSnapshot snap, boolean force,
                             ProgressLog progress, TokenAccumulator tokens) {
        Project p = linked(userId, projectId);
        RepoMap cached = RepoMapRenderer.parse(p.getRepoMap());
        if (!force && cached != null && p.getRepoCommitSha().equals(cached.sha())) {
            progress.emit("Map: reusing map for " + cached.sha().substring(0, 7));
            return cached;
        }
        RepoMap map = mapper.build(p.getName(), p.getRepoCommitSha(), snap, progress, tokens);
        p.setRepoMap(RepoMapRenderer.toJson(map));
        projectRepo.save(p);
        return map;
    }

    /** Downloads the whole repo once for the explorer and the map builder. */
    public RepoSnapshot snapshot(UUID userId, RepoReader reader) {
        return withInstallation(userId, i -> reader.snapshot());
    }

    public RepoMap map(UUID userId, UUID projectId) {
        return RepoMapRenderer.parse(projects.get(userId, projectId).getRepoMap());
    }

    /** bulletId -> the evidence spans it traces to. Bullets with no confident match are omitted. */
    public Map<UUID, List<SourceTracer.Source>> bulletSources(UUID userId, UUID projectId) {
        Project project = projects.get(userId, projectId);
        List<RepoExplorer.Evidence> ev = new java.util.ArrayList<>(evidence(project));
        ev.addAll(moduleEvidence(RepoMapRenderer.parse(project.getRepoMap())));
        Map<UUID, List<SourceTracer.Source>> out = new LinkedHashMap<>();
        if (ev.isEmpty()) return out;
        for (Bullet b : bullets.findByProjectIdOrderByCreatedAtAsc(projectId)) {
            List<SourceTracer.Source> s = SourceTracer.trace(b.getText(), ev);
            if (!s.isEmpty()) out.put(b.getId(), s);
        }
        return out;
    }

    /** Summarized modules as traceable sources — a bullet about "the polling job store" links to that module's central file. */
    static List<RepoExplorer.Evidence> moduleEvidence(RepoMap map) {
        if (map == null) return List.of();
        return map.modules().stream()
                .filter(m -> m.summary() != null && !m.summary().isBlank())
                .map(m -> new RepoExplorer.Evidence("module " + m.path(), m.summary(), m.topFile(), 1, 1, null,
                        m.path() + "\n" + m.summary() + "\n" + String.join("\n", m.symbols())))
                .toList();
    }

    static String renderEvidence(String repo, String sha, List<RepoExplorer.Evidence> evidence, int maxChars) {
        if (evidence.isEmpty() || maxChars <= 0) return null;
        StringBuilder sb = new StringBuilder("Verified evidence from " + repo + "@" + sha.substring(0, Math.min(7, sha.length()))
                + " (verbatim spans re-read by AnvilCV):\n");
        for (RepoExplorer.Evidence e : evidence) {
            String ref = e.commit() != null ? "commit " + e.commit() : e.path() + ":" + e.startLine() + "-" + e.endLine();
            String block = "\n[" + e.field() + "] " + e.claim() + "\n" + ref + "\n" + e.text() + "\n";
            if (sb.length() + block.length() > maxChars) break;
            sb.append(block);
        }
        return sb.toString();
    }

    private static void set(String value, java.util.function.Consumer<String> setter) {
        if (value != null && !value.isBlank()) setter.accept(value);
    }

    public Project linked(UUID userId, UUID projectId) {
        Project p = projects.get(userId, projectId);
        if (p.getRepoCommitSha() == null || p.getGithubUrl() == null) {
            throw new GithubException("Project is not linked to a GitHub repository");
        }
        return p;
    }

    static String repoName(Project p) {
        return p.getGithubUrl().replaceFirst("^https://github\\.com/", "");
    }

    private long installation(UUID userId) {
        requireConfigured();
        return installs.findById(userId).orElseThrow(GithubException.NotConnected::new).getInstallationId();
    }

    private <T> T withInstallation(UUID userId, LongFunction<T> call) {
        long inst = installation(userId);
        try {
            return call.apply(inst);
        } catch (GithubException.NotConnected e) {
            installs.deleteById(userId); // uninstalled on GitHub — drop the stale link
            throw e;
        }
    }

    private void requireConfigured() {
        if (!auth.isConfigured()) throw new GithubException.NotConfigured();
    }
}
