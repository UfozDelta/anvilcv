package com.resumepipeline.github;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumepipeline.llm.LlmClient;
import com.resumepipeline.project.Project;
import com.resumepipeline.project.ProjectRepository;
import com.resumepipeline.project.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
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
    private final String webBase;

    public GithubService(GithubAppAuth auth, GithubClient client, GithubInstallationRepository installs,
                         ProjectService projects, ProjectRepository projectRepo,
                         @Value("${github.web-base:https://github.com}") String webBase) {
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

    /** Install page for the app. GitHub hands {@code state} back to the callback. */
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
                .orElseThrow(() -> new GithubException("No AnvilCV installation found on this GitHub account — install the app first"));
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
        p.setRepoContext(renderEvidence(repoName(p), p.getRepoCommitSha(), outcome.evidence()));
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

    static String renderEvidence(String repo, String sha, List<RepoExplorer.Evidence> evidence) {
        if (evidence.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("Verified evidence from " + repo + "@" + sha.substring(0, Math.min(7, sha.length()))
                + " (verbatim spans re-read by AnvilCV):\n");
        for (RepoExplorer.Evidence e : evidence) {
            String ref = e.commit() != null ? "commit " + e.commit() : e.path() + ":" + e.startLine() + "-" + e.endLine();
            String block = "\n[" + e.field() + "] " + e.claim() + "\n" + ref + "\n" + e.text() + "\n";
            if (sb.length() + block.length() > MAX_REPO_CONTEXT_CHARS) break;
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
