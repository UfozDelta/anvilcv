package com.resumepipeline.project;

import com.resumepipeline.bullet.BulletRepository;
import com.resumepipeline.llm.GithubContextFetcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Caveat: under MockitoExtension there is no Spring proxy, so the {@code @Async}
 * {@code fetchAndCacheRepoContext} runs INLINE on the test thread — these tests
 * verify it is invoked, not that it is dispatched asynchronously.
 */
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock ProjectRepository repo;
    @Mock BulletRepository bulletRepo;
    @Mock GithubContextFetcher githubFetcher;
    @InjectMocks ProjectService service;

    @Test
    void getThrows404WhenMissing() {
        UUID user = UUID.randomUUID(), id = UUID.randomUUID();
        when(repo.findByUserIdAndId(user, id)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.get(user, id));
    }

    @Test
    void createWithoutGithubUrlDoesNotFetch() {
        UUID user = UUID.randomUUID();
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(user, Project.Kind.PROJECT, "P", "desc", null, null, null, null, null);

        verifyNoInteractions(githubFetcher);
    }

    @Test
    void createWithGithubUrlTriggersFetch() {
        UUID user = UUID.randomUUID();
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // fetchAndCacheRepoContext re-reads the project by id; saved id is null here, so
        // findById(null) is fine to stub as empty — we only assert the fetch happened.
        when(repo.findById(any())).thenReturn(Optional.empty());
        when(githubFetcher.fetch("https://github.com/x/y")).thenReturn("README");

        service.create(user, Project.Kind.PROJECT, "P", "desc", "https://github.com/x/y",
                null, null, null, null);

        verify(githubFetcher).fetch("https://github.com/x/y");
    }

    @Test
    void updateDoesNotFetchWhenUrlUnchanged() {
        UUID user = UUID.randomUUID(), id = UUID.randomUUID();
        Project existing = new Project(user, Project.Kind.PROJECT, "P", "d", null, null, null, null, null);
        existing.setGithubUrl("https://github.com/x/y");
        when(repo.findByUserIdAndId(user, id)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(user, id, "P", "d", "https://github.com/x/y",
                null, null, null, null, null, null, null, null, null);

        verifyNoInteractions(githubFetcher);
    }

    @Test
    void updateFetchesWhenUrlChanged() {
        UUID user = UUID.randomUUID(), id = UUID.randomUUID();
        Project existing = new Project(user, Project.Kind.PROJECT, "P", "d", null, null, null, null, null);
        existing.setGithubUrl("https://github.com/old/repo");
        when(repo.findByUserIdAndId(user, id)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findById(any())).thenReturn(Optional.empty());
        when(githubFetcher.fetch("https://github.com/new/repo")).thenReturn("README");

        service.update(user, id, "P", "d", "https://github.com/new/repo",
                null, null, null, null, null, null, null, null, null);

        verify(githubFetcher).fetch("https://github.com/new/repo");
    }

    @Test
    void deleteRemovesBulletsBeforeProject() {
        UUID user = UUID.randomUUID(), id = UUID.randomUUID();
        Project existing = new Project(user, Project.Kind.PROJECT, "P", "d", null, null, null, null, null);
        when(repo.findByUserIdAndId(user, id)).thenReturn(Optional.of(existing));

        service.delete(user, id);

        InOrder order = inOrder(bulletRepo, repo);
        order.verify(bulletRepo).deleteByProjectId(existing.getId());
        order.verify(repo).deleteById(existing.getId());
    }

    @Test
    void listByKindDelegatesToRepo() {
        UUID user = UUID.randomUUID();
        service.listByKind(user, Project.Kind.EXPERIENCE);
        verify(repo).findAllByUserIdAndKindOrderByCreatedAtDesc(user, Project.Kind.EXPERIENCE);
    }
}
