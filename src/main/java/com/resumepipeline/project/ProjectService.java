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

    public Project update(UUID userId, UUID id, String name, String description,
                          String githubUrl, String techStack, String yourRole,
                          String ownership, String scaleImpact, String hardestProblem,
                          String technicalDecisions, String userImpact, String securityPosture,
                          String title, String company, String location, String dates) {
        Project p = get(userId, id);
        if (name != null)        p.setName(name);
        if (description != null) p.setDescription(description);
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
        try {
            String context = githubFetcher.fetch(githubUrl);
            repo.findById(projectId).ifPresent(p -> {
                p.setRepoContext(context);
                repo.save(p);
            });
        } catch (Exception e) {
            log.warn("Failed to cache repo context for project {}: {}", projectId, e.getMessage());
        }
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Project p = get(userId, id);
        bulletRepo.deleteByProjectId(p.getId());
        repo.deleteById(p.getId());
    }
}
