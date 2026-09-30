package com.resumepipeline.github;

import com.resumepipeline.project.ProjectRepository;
import com.resumepipeline.project.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GithubServiceTest {

    private final GithubAppAuth auth = mock(GithubAppAuth.class);
    private final GithubClient client = mock(GithubClient.class);
    private final GithubInstallationRepository installs = mock(GithubInstallationRepository.class);
    private GithubService service;
    private final UUID user = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(auth.isConfigured()).thenReturn(true);
        service = new GithubService(auth, client, installs, mock(ProjectService.class),
                mock(ProjectRepository.class), mock(com.resumepipeline.bullet.BulletRepository.class), mock(RepoMapService.class), "https://github.com");
    }

    @Test
    void connectRejectsInstallationIdNotOwnedByTheCodesUser() {
        when(client.exchangeCode("c")).thenReturn("user-tok");
        when(client.userInstallations("user-tok")).thenReturn(List.of(new GithubClient.Installation(1, "me")));

        // Forged ?installation_id=999 pointing at someone else's installation.
        assertThrows(GithubException.class, () -> service.connect(user, "c", 999L));
        verify(installs, never()).save(any());
    }

    @Test
    void connectStoresOnlyInstallationIdNeverTheToken() {
        when(client.exchangeCode("c")).thenReturn("user-tok");
        when(client.userInstallations("user-tok")).thenReturn(List.of(new GithubClient.Installation(1, "me")));
        when(installs.findById(user)).thenReturn(Optional.empty());

        service.connect(user, "c", 1L);

        verify(installs).save(argThat(i -> i.getInstallationId() == 1 && i.getAccountLogin().equals("me")));
    }

    @Test
    void uninstalledOnGithubDropsTheStaleLink() {
        when(installs.findById(user)).thenReturn(Optional.of(new GithubInstallation(user, 1, "me")));
        when(client.listRepos(1)).thenThrow(new GithubException.NotConnected());

        assertThrows(GithubException.NotConnected.class, () -> service.repos(user));
        verify(installs).deleteById(user);
    }

    @Test
    void applyExploreKeepsUserFieldsTheExplorerLeftBlankAndGroundsGeneration() {
        ProjectService projects = mock(ProjectService.class);
        ProjectRepository repo = mock(ProjectRepository.class);
        service = new GithubService(auth, client, installs, projects, repo, mock(com.resumepipeline.bullet.BulletRepository.class), mock(RepoMapService.class), "https://github.com");
        UUID pid = UUID.randomUUID();
        com.resumepipeline.project.Project p = new com.resumepipeline.project.Project(
                user, com.resumepipeline.project.Project.Kind.PROJECT, "app", "d", null, null, null, null, null);
        p.setGithubUrl("https://github.com/me/app");
        p.setRepoCommitSha("abcdef1234");
        p.setYourRole("Solo author — typed by the user");
        when(projects.get(user, pid)).thenReturn(p);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = new com.resumepipeline.llm.LlmClient.ExtractResult("app", "Spring Boot 3.4", "Overview.", "", "", "", "", "", "", "",
                List.of("backend"), List.of());
        var ev = new RepoExplorer.Evidence("techStack", "Spring Boot parent", "pom.xml", 3, 4, null, "<version>3.4.0</version>");
        service.applyExplore(user, pid, new RepoExplorer.Outcome(result, List.of(ev), List.of(), 3));

        assertEquals("Spring Boot 3.4", p.getTechStack());
        assertEquals("Overview.", p.getContextDescription());
        assertEquals("Solo author — typed by the user", p.getYourRole()); // blank from explorer, user value kept
        assertTrue(p.getRepoContext().contains("pom.xml:3-4\n<version>3.4.0</version>"));
        assertEquals(List.of(ev), service.evidence(p)); // JSON round-trip
    }

    @Test
    void notConfiguredRefusesBeforeTouchingGithub() {
        when(auth.isConfigured()).thenReturn(false);
        assertThrows(GithubException.NotConfigured.class, () -> service.repos(user));
        verifyNoInteractions(client);
    }
}
