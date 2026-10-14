package com.resumepipeline.onboarding;

import com.resumepipeline.application.ApplicationRepository;
import com.resumepipeline.auth.AppUser;
import com.resumepipeline.auth.AppUserRepository;
import com.resumepipeline.bullet.BulletRepository;
import com.resumepipeline.project.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Where a user is in the guided first run, read from what they have actually created. */
@Service
public class OnboardingService {

    /** @param onboarded false until the user finishes or skips the guide */
    public record Status(boolean onboarded, long entries, long approvedBullets, long applications, boolean downloaded) {}

    private final AppUserRepository users;
    private final ProjectRepository projects;
    private final BulletRepository bullets;
    private final ApplicationRepository applications;

    public OnboardingService(AppUserRepository users, ProjectRepository projects,
                             BulletRepository bullets, ApplicationRepository applications) {
        this.users = users;
        this.projects = projects;
        this.bullets = bullets;
        this.applications = applications;
    }

    @Transactional(readOnly = true)
    public Status status(UUID userId) {
        AppUser u = users.findById(userId).orElseThrow();
        return new Status(u.getOnboardedAt() != null, projects.countByUserId(userId),
                bullets.countApprovedByUserId(userId), applications.countByUserId(userId),
                u.getFirstDownloadAt() != null);
    }

    /** The user finished or skipped the guide. Repeating it keeps the first time. */
    @Transactional
    public void complete(UUID userId) {
        AppUser u = users.findById(userId).orElseThrow();
        if (u.getOnboardedAt() == null) u.setOnboardedAt(OffsetDateTime.now());
    }

    /** The user's first Download PDF click. Repeating it keeps the first time. */
    @Transactional
    public void downloaded(UUID userId) {
        AppUser u = users.findById(userId).orElseThrow();
        if (u.getFirstDownloadAt() == null) u.setFirstDownloadAt(OffsetDateTime.now());
    }
}
