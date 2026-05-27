package com.resumepipeline.project;

import com.resumepipeline.bullet.BulletRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository repo;
    private final BulletRepository bulletRepo;

    public ProjectService(ProjectRepository repo, BulletRepository bulletRepo) {
        this.repo = repo;
        this.bulletRepo = bulletRepo;
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

    public Project create(UUID userId, Project.Kind kind, String name, String description, String sourcePath,
                          String title, String company, String location, String dates) {
        return repo.save(new Project(userId, kind, name, description, sourcePath, title, company, location, dates));
    }

    public Project update(UUID userId, UUID id, String name, String description, String sourcePath,
                          String title, String company, String location, String dates) {
        Project p = get(userId, id);
        if (name != null)        p.setName(name);
        if (description != null) p.setDescription(description);
        p.setSourcePath(sourcePath);
        p.setTitle(title);
        p.setCompany(company);
        p.setLocation(location);
        p.setDates(dates);
        return repo.save(p);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Project p = get(userId, id);
        bulletRepo.deleteByProjectId(p.getId());
        repo.deleteById(p.getId());
    }
}
