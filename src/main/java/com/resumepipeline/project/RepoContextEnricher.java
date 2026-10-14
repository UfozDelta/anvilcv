package com.resumepipeline.project;

import com.resumepipeline.llm.GithubContextFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Its own bean so ProjectService calls it through the Spring proxy and @Async actually dispatches. */
@Component
public class RepoContextEnricher {

    private static final Logger log = LoggerFactory.getLogger(RepoContextEnricher.class);

    private final ProjectRepository repo;
    private final GithubContextFetcher githubFetcher;

    public RepoContextEnricher(ProjectRepository repo, GithubContextFetcher githubFetcher) {
        this.repo = repo;
        this.githubFetcher = githubFetcher;
    }

    @Async
    public void fetchAndCacheRepoContext(UUID projectId, String githubUrl) {
        long start = System.currentTimeMillis();
        try {
            String context = githubFetcher.fetch(githubUrl);
            repo.findById(projectId).ifPresent(p -> {
                p.setRepoContext(context);
                repo.save(p);
            });
            log.info("PROJECT_ENRICH project={} ok=true ms={}", projectId, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("PROJECT_ENRICH project={} ok=false ms={} cause={}",
                    projectId, System.currentTimeMillis() - start, e.getMessage());
        }
    }
}
