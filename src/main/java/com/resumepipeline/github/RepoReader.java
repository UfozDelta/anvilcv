package com.resumepipeline.github;

import java.util.List;

/**
 * Read-only view of one repo at one commit, on one user's installation. The explorer's tool
 * surface. Once a {@link RepoSnapshot} is attached, tree and file reads come from it instead of
 * the API — the whole repo already arrived in one tarball.
 */
public class RepoReader {

    private final GithubClient client;
    private final long installationId;
    private final String repo;
    private final String sha;
    private final RepoSnapshot snapshot;

    public RepoReader(GithubClient client, long installationId, String repo, String sha) {
        this(client, installationId, repo, sha, null);
    }

    private RepoReader(GithubClient client, long installationId, String repo, String sha, RepoSnapshot snapshot) {
        this.client = client;
        this.installationId = installationId;
        this.repo = repo;
        this.sha = sha;
        this.snapshot = snapshot;
    }

    public RepoReader withSnapshot(RepoSnapshot snapshot) {
        return new RepoReader(client, installationId, repo, sha, snapshot);
    }

    public String repo() { return repo; }
    public String sha()  { return sha; }

    public GithubClient.Tree tree() {
        if (snapshot != null) {
            return new GithubClient.Tree(snapshot.files().entrySet().stream()
                    .map(e -> new GithubClient.TreeEntry(e.getKey(), e.getValue().length())).toList(), snapshot.truncated());
        }
        return client.tree(installationId, repo, sha);
    }

    public String read(String path) {
        if (snapshot != null) {
            String body = snapshot.read(path);
            if (body == null) throw new GithubException("not found (or skipped: vendored, binary, or too large)");
            return body;
        }
        return client.readFile(installationId, repo, path, sha);
    }

    public List<GithubClient.SearchHit> search(String query) {
        return client.searchCode(installationId, repo, query);
    }

    public List<GithubClient.Commit> log(String path) {
        return client.commits(installationId, repo, sha, path);
    }

    public RepoSnapshot snapshot() {
        return snapshot != null ? snapshot : client.snapshot(installationId, repo, sha);
    }
}
