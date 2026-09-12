package com.resumepipeline.project;

import com.resumepipeline.bullet.BulletRepository;
import com.resumepipeline.llm.GithubContextFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository repo;
    private final BulletRepository bulletRepo;
    private final GithubContextFetcher githubFetcher;

    public ProjectService(ProjectRepository repo, BulletRepository bulletRepo, GithubContextFetcher githubFetcher) {
        this.repo = repo;
        this.bulletRepo = bulletRepo;
        this.githubFetcher = githubFetcher;
    }

    public List<Project> list(UUID userId) {
        return repo.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Project> listByKind(UUID userId, Project.Kind kind) {
        return repo.findAllByUserIdAndKindOrderByCreatedAtDesc(userId, kind);
    }

    public Project get(UUID userId, UUID id) {
        return repo.findByUserIdAndId(userId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + id));
    }

    public Project create(UUID userId, Project.Kind kind, String name, String description,
                          String githubUrl, String title, String company, String location, String dates) {
        Project p = new Project(userId, kind, name, description, null, title, company, location, dates);
        p.setGithubUrl(githubUrl);
        Project saved = repo.save(p);
        if (githubUrl != null && !githubUrl.isBlank()) {
            fetchAndCacheRepoContext(saved.getId(), githubUrl);
        }
        return saved;
    }

    public Project update(UUID userId, UUID id, String name, String description, String contextDescription,
                          String githubUrl, String techStack, String yourRole,
                          String ownership, String scaleImpact, String hardestProblem,
                          String technicalDecisions, String userImpact, String securityPosture,
                          String title, String company, String location, String dates) {
        Project p = get(userId, id);
        if (name != null)        p.setName(name);
        if (description != null) p.setDescription(description);
        p.setContextDescription(contextDescription);
        String oldUrl = p.getGithubUrl();
        p.setGithubUrl(githubUrl);
        p.setTechStack(techStack);
        p.setYourRole(yourRole);
        p.setOwnership(ownership);
        p.setScaleImpact(scaleImpact);
        p.setHardestProblem(hardestProblem);
        p.setTechnicalDecisions(technicalDecisions);
        p.setUserImpact(userImpact);
        p.setSecurityPosture(securityPosture);
        p.setTitle(title);
        p.setCompany(company);
        p.setLocation(location);
        p.setDates(dates);
        Project saved = repo.save(p);
        boolean urlChanged = githubUrl != null && !githubUrl.equals(oldUrl);
        if (urlChanged) {
            fetchAndCacheRepoContext(saved.getId(), githubUrl);
        }
        return saved;
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

    @Transactional
    public void delete(UUID userId, UUID id) {
        Project p = get(userId, id);
        bulletRepo.deleteByProjectId(p.getId());
        repo.deleteById(p.getId());
    }

    @Transactional
    public Project duplicate(UUID userId, UUID id) {
        Project src = get(userId, id);
        Project copy = new Project(userId, src.getKind(), src.getName() + " (copy)", src.getDescription(),
                null, src.getTitle(), src.getCompany(), src.getLocation(), src.getDates());
        copy.setContextDescription(src.getContextDescription());
        copy.setGithubUrl(src.getGithubUrl());
        copy.setTechStack(src.getTechStack());
        copy.setYourRole(src.getYourRole());
        copy.setOwnership(src.getOwnership());
        copy.setScaleImpact(src.getScaleImpact());
        copy.setHardestProblem(src.getHardestProblem());
        copy.setTechnicalDecisions(src.getTechnicalDecisions());
        copy.setUserImpact(src.getUserImpact());
        copy.setSecurityPosture(src.getSecurityPosture());
        Project saved = repo.save(copy);

        for (var b : bulletRepo.findByProjectIdOrderByCreatedAtAsc(src.getId())) {
            var clone = new com.resumepipeline.bullet.Bullet(saved.getId(), b.getText(), b.getTags(), b.getCategory());
            clone.setStatus(b.getStatus());
            bulletRepo.save(clone);
        }
        return saved;
    }
}
