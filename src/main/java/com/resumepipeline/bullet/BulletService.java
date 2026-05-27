package com.resumepipeline.bullet;

import com.resumepipeline.llm.CategoryLenses;
import com.resumepipeline.llm.LlmClient;
import com.resumepipeline.llm.RepoContextReader;
import com.resumepipeline.progress.ProgressLog;
import com.resumepipeline.project.Project;
import com.resumepipeline.project.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class BulletService {

    private static final Logger log = LoggerFactory.getLogger(BulletService.class);

    private final BulletRepository repo;
    private final ProjectService projectService;
    private final LlmClient llm;
    private final RepoContextReader repoContextReader;

    public BulletService(BulletRepository repo, ProjectService projectService,
                         LlmClient llm, RepoContextReader repoContextReader) {
        this.repo = repo;
        this.projectService = projectService;
        this.llm = llm;
        this.repoContextReader = repoContextReader;
    }

    public List<Bullet> listForProject(UUID userId, UUID projectId) {
        projectService.get(userId, projectId); // verify ownership
        return repo.findByProjectIdOrderByCreatedAtAsc(projectId);
    }

    public Bullet create(UUID userId, UUID projectId, String text, String[] tags) {
        projectService.get(userId, projectId); // verify ownership
        return repo.save(new Bullet(projectId, text, tags));
    }

    public Bullet update(UUID userId, UUID bulletId, String text, String[] tags) {
        Bullet b = repo.findById(bulletId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bullet not found: " + bulletId));
        projectService.get(userId, b.getProjectId()); // verify ownership
        if (text != null) b.setText(text);
        if (tags != null) b.setTags(tags);
        return repo.save(b);
    }

    public void delete(UUID userId, UUID bulletId) {
        Bullet b = repo.findById(bulletId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bullet not found: " + bulletId));
        projectService.get(userId, b.getProjectId()); // verify ownership
        repo.deleteById(bulletId);
    }

    /** Single un-categorized generation. Persists bullets with category="general". */
    public List<Bullet> generateForProject(UUID userId, UUID projectId) {
        return generateForProjectAndCategory(userId, projectId, "general", ProgressLog.noOp());
    }

    /** Generate bullets for one project and one category lens. */
    public List<Bullet> generateForProjectAndCategory(UUID userId, UUID projectId, String category, ProgressLog progress) {
        Project p = projectService.get(userId, projectId);
        String repoContext = repoContextReader.read(p.getSourcePath());

        LlmClient.SourceKind sk = p.getKind() == Project.Kind.EXPERIENCE
                ? LlmClient.SourceKind.EXPERIENCE
                : LlmClient.SourceKind.PROJECT;

        String cat = (category == null || category.isBlank()) ? "general" : category;

        LlmClient.BulletGenerationResult result = llm.generateBullets(
                new LlmClient.GenerateBulletsRequest(
                        userId, sk, cat,
                        p.getName(), p.getDescription(), repoContext,
                        p.getTitle(), p.getCompany(), p.getLocation(), p.getDates()),
                progress);

        return result.bullets().stream()
                .map(g -> repo.save(new Bullet(projectId, g.text(), g.tags().toArray(new String[0]), cat)))
                .toList();
    }

    public List<Bullet> generateBank(UUID userId, UUID projectId, List<String> categories, ProgressLog progress) {
        if (categories == null || categories.isEmpty()) {
            throw new IllegalArgumentException("categories cannot be empty");
        }
        for (String c : categories) {
            if (!CategoryLenses.LENSES.containsKey(c)) {
                throw new IllegalArgumentException("Unknown category: " + c);
            }
        }
        List<Bullet> combined = new ArrayList<>();
        int total = categories.size();
        for (int i = 0; i < total; i++) {
            String c = categories.get(i);
            progress.emit("[" + (i + 1) + "/" + total + "] Starting category: " + c);
            log.info("Generating bank for project {} category {}", projectId, c);
            combined.addAll(generateForProjectAndCategory(userId, projectId, c, progress));
        }
        progress.emit("Done — generated " + combined.size() + " bullets across " + total + " categories.");
        return combined;
    }
}
