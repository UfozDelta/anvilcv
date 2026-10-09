package com.resumepipeline.project;

import com.resumepipeline.application.ApplicationRepository;
import com.resumepipeline.bullet.BulletRepository;
import com.resumepipeline.bullet.Story;
import com.resumepipeline.bullet.StoryRepository;
import com.resumepipeline.llm.BulletTextRules;
import com.resumepipeline.llm.GithubContextFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository repo;
    private final BulletRepository bulletRepo;
    private final GithubContextFetcher githubFetcher;
    // A heading edit changes every PDF that prints this entry — see markPdfStale.
    private final ApplicationRepository applicationRepo;
    private final StoryRepository storyRepo;

    public ProjectService(ProjectRepository repo, BulletRepository bulletRepo, GithubContextFetcher githubFetcher,
                          ApplicationRepository applicationRepo, StoryRepository storyRepo) {
        this.repo = repo;
        this.bulletRepo = bulletRepo;
        this.githubFetcher = githubFetcher;
        this.applicationRepo = applicationRepo;
        this.storyRepo = storyRepo;
    }

    public List<Project> list(UUID userId) {
        return repo.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Project> listByKind(UUID userId, Project.Kind kind) {
        return repo.findAllByUserIdAndKindOrderByCreatedAtDesc(userId, kind);
    }

    /** Bullet count per project id in one grouped query; ids without bullets map to 0. */
    public Map<UUID, Long> bulletCounts(Collection<UUID> projectIds) {
        Map<UUID, Long> counts = new HashMap<>();
        if (projectIds.isEmpty()) return counts;
        for (Object[] row : bulletRepo.countGroupedByProjectId(projectIds)) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    public long bulletCount(UUID projectId) {
        return bulletRepo.countByProjectIdAndStatusNot(projectId, "REJECTED");
    }

    /**
     * Distinct stories a resume can draw on: non-REJECTED bullets selection may pick on its own
     * ({@link BulletTextRules#autoSelectable}), a storyless bullet counting as its own story.
     */
    public long usableStoryCount(UUID projectId) {
        return bulletRepo.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
                .filter(b -> !"REJECTED".equals(b.getStatus())
                        && BulletTextRules.autoSelectable(b.getStatus(), b.getText()))
                .map(b -> b.getStoryId() != null ? b.getStoryId() : b.getId())
                .distinct().count();
    }

    public Project get(UUID userId, UUID id) {
        return repo.findByUserIdAndId(userId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + id));
    }

    /** True when the free-text dates end in "Present" / "Current" / "Now" / "Ongoing". */
    public static boolean looksCurrent(String dates) {
        return dates != null && CURRENT_END.matcher(dates).find();
    }
    private static final java.util.regex.Pattern CURRENT_END =
            java.util.regex.Pattern.compile("(present|current|now|ongoing)\\s*$", java.util.regex.Pattern.CASE_INSENSITIVE);

    public Project create(UUID userId, Project.Kind kind, String name, String description,
                          String githubUrl, String title, String company, String location, String dates) {
        return create(userId, kind, name, description, githubUrl, title, company, location, dates, null);
    }

    /** `current` null = derive it from the dates text (imports, older clients). */
    public Project create(UUID userId, Project.Kind kind, String name, String description,
                          String githubUrl, String title, String company, String location, String dates,
                          Boolean current) {
        Project p = new Project(userId, kind, name, description, null, title, company, location, dates);
        p.setCurrent(current != null ? current : looksCurrent(dates));
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
        return update(userId, id, name, description, contextDescription, githubUrl, techStack, yourRole,
                ownership, scaleImpact, hardestProblem, technicalDecisions, userImpact, securityPosture,
                title, company, location, dates, null);
    }

    /** `current` null = leave the flag alone unless new dates text says "Present". */
    public Project update(UUID userId, UUID id, String name, String description, String contextDescription,
                          String githubUrl, String techStack, String yourRole,
                          String ownership, String scaleImpact, String hardestProblem,
                          String technicalDecisions, String userImpact, String securityPosture,
                          String title, String company, String location, String dates, Boolean current) {
        Project p = get(userId, id);
        List<Object> printedBefore = printed(p);
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
        if (current != null) p.setCurrent(current);
        else if (looksCurrent(dates)) p.setCurrent(true);
        Project saved = repo.save(p);
        if (!printed(saved).equals(printedBefore)) markPdfStale(userId, saved.getId());
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

    /** The fields ApplicationRenderer prints in this entry's heading; the rest only feed prompts. */
    private static List<Object> printed(Project p) {
        return Arrays.asList(p.getName(), p.getTitle(), p.getCompany(), p.getLocation(), p.getDates(), p.getTechStack());
    }

    /** Flag every PDF printing a bullet of this project. Never fails the edit it follows. */
    private void markPdfStale(UUID userId, UUID projectId) {
        try {
            applicationRepo.markPdfStaleForProject(userId, projectId);
        } catch (RuntimeException e) {
            log.warn("Could not flag application PDFs stale after project edit: {}", e.getMessage());
        }
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Project p = get(userId, id);
        // Before the bullets go: the flag finds affected pages through them.
        markPdfStale(userId, p.getId());
        bulletRepo.deleteByProjectId(p.getId());
        repo.deleteById(p.getId());
    }

    @Transactional
    public Project duplicate(UUID userId, UUID id) {
        Project src = get(userId, id);
        Project copy = new Project(userId, src.getKind(), src.getName() + " (copy)", src.getDescription(),
                null, src.getTitle(), src.getCompany(), src.getLocation(), src.getDates());
        copy.setCurrent(src.isCurrent());
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

        // Stories get fresh ids, and the copied wordings point at the copies.
        Map<UUID, UUID> storyCopy = new HashMap<>();
        for (Story s : storyRepo.findByProjectIdOrderByCreatedAtAsc(src.getId())) {
            UUID newId = UUID.randomUUID();
            storyCopy.put(s.getId(), newId);
            storyRepo.save(new Story(newId, saved.getId(), s.getTitle(), s.getEvidence(), s.getLenses()));
        }
        for (var b : bulletRepo.findByProjectIdOrderByCreatedAtAsc(src.getId())) {
            var clone = new com.resumepipeline.bullet.Bullet(saved.getId(), b.getText(), b.getTags(), b.getCategory());
            clone.setAngle(b.getAngle());
            clone.setJudgeNote(b.getJudgeNote());
            clone.setStatus(b.getStatus());
            clone.setStoryId(b.getStoryId() == null ? null : storyCopy.get(b.getStoryId()));
            bulletRepo.save(clone);
        }
        return saved;
    }
}
