package com.resumepipeline.onboarding;

import com.resumepipeline.application.ApplicationRepository;
import com.resumepipeline.auth.AppUser;
import com.resumepipeline.auth.AppUserRepository;
import com.resumepipeline.bullet.BulletRepository;
import com.resumepipeline.project.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnboardingServiceTest {

    final UUID id = UUID.randomUUID();
    AppUserRepository users = mock(AppUserRepository.class);
    ProjectRepository projects = mock(ProjectRepository.class);
    BulletRepository bullets = mock(BulletRepository.class);
    ApplicationRepository applications = mock(ApplicationRepository.class);
    OnboardingService service = new OnboardingService(users, projects, bullets, applications);
    AppUser user = new AppUser("u", "u@example.com", "x");

    @BeforeEach
    void setUp() {
        when(users.findById(id)).thenReturn(Optional.of(user));
    }

    @Test
    void aNewUserIsNotOnboardedAndHasNothingYet() {
        var s = service.status(id);
        assertFalse(s.onboarded());
        assertFalse(s.downloaded());
        assertEquals(0, s.entries());
    }

    @Test
    void statusCountsWhatTheUserHasCreated() {
        when(projects.countByUserId(id)).thenReturn(3L);
        when(bullets.countApprovedByUserId(id)).thenReturn(7L);
        when(applications.countByUserId(id)).thenReturn(2L);
        var s = service.status(id);
        assertEquals(3, s.entries());
        assertEquals(7, s.approvedBullets());
        assertEquals(2, s.applications());
    }

    @Test
    void completeAndDownloadedKeepTheFirstTime() {
        service.complete(id);
        service.downloaded(id);
        OffsetDateTime done = user.getOnboardedAt(), dl = user.getFirstDownloadAt();
        assertNotNull(done);
        assertNotNull(dl);

        service.complete(id);
        service.downloaded(id);
        assertSame(done, user.getOnboardedAt());
        assertSame(dl, user.getFirstDownloadAt());
        assertTrue(service.status(id).onboarded());
        assertTrue(service.status(id).downloaded());
    }
}
