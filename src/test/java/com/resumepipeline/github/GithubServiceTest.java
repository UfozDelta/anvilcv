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
                mock(ProjectRepository.class), "https://github.com");
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
    void notConfiguredRefusesBeforeTouchingGithub() {
        when(auth.isConfigured()).thenReturn(false);
        assertThrows(GithubException.NotConfigured.class, () -> service.repos(user));
        verifyNoInteractions(client);
    }
}
