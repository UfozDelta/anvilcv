package com.resumepipeline.github;

import java.util.List;

/** Read-only view of one repo at one commit, on one user's installation. The explorer's tool surface. */
public class RepoReader {

    private final GithubClient client;
    private final long installationId;
    private final String repo;
    private final String sha;

    public RepoReader(GithubClient client, long installationId, String repo, String sha) {
        this.client = client;
        this.installationId = installationId;
        this.repo = repo;
        this.sha = sha;
    }

    public String repo() { return repo; }
    public String sha()  { return sha; }

    public GithubClient.Tree tree() {
        return client.tree(installationId, repo, sha);
    }

    public String read(String path) {
        return client.readFile(installationId, repo, path, sha);
    }

    public List<GithubClient.SearchHit> search(String query) {
        return client.searchCode(installationId, repo, query);
    }

    public List<GithubClient.Commit> log(String path) {
        return client.commits(installationId, repo, sha, path);
    }
}
