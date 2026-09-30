package com.resumepipeline.github;

/** Failures the GitHub layer surfaces to controllers and to the repo explorer. */
public class GithubException extends RuntimeException {

    public GithubException(String message) { super(message); }

    /** No GitHub App env configured on this deploy. */
    public static class NotConfigured extends GithubException {
        public NotConfigured() { super("GitHub App is not configured on this server"); }
    }

    /** The user has no linked installation, or it was uninstalled on GitHub's side. */
    public static class NotConnected extends GithubException {
        public NotConnected() { super("GitHub is not connected — connect it in Settings"); }
    }

    /** Primary or secondary rate limit hit; {@code resetSeconds} is how long GitHub asked us to wait. */
    public static class RateLimited extends GithubException {
        private final long resetSeconds;
        public RateLimited(long resetSeconds) {
            super("GitHub rate limit hit — retry in " + resetSeconds + "s");
            this.resetSeconds = resetSeconds;
        }
        public long resetSeconds() { return resetSeconds; }
    }
}
