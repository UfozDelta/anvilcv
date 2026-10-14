package com.resumepipeline.quota;

import com.resumepipeline.auth.AppUser;
import com.resumepipeline.auth.AppUserRepository;
import com.resumepipeline.project.Project;
import com.resumepipeline.project.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * What a user's plan lets them do. A tailor is one finished application; a generation is one
 * finished bullet-writing run. Checked before the work starts, recorded once it finishes, so a
 * failed run costs nothing. Two requests racing can slip one past the cap; the caps are small and
 * the work cheap, so that is accepted rather than locking.
 *
 * <p>The free plan is limited by entries (up to {@value #FREE_PROJECTS} projects and
 * {@value #FREE_EXPERIENCES} work experience to write bullets for) and by tailors per month. Paid
 * plans are limited by runs. A user flagged {@code quota_exempt}, and every admin, is never refused.
 */
@Service
public class QuotaService {

    public static final int FREE_PROJECTS = 2;
    public static final int FREE_EXPERIENCES = 1;

    /** @param limit null means no cap of this kind applies to the plan */
    public record Meter(long used, Integer limit) {}
    public record Entries(long used, int cap) {}
    public record Usage(String plan, boolean exempt, Meter tailors, Meter generations,
                        Entries projects, Entries experiences, Instant resetsAt) {}

    private final AppUserRepository users;
    private final ProjectRepository projects;
    private final UsageEventRepository events;
    private final Clock clock;

    @Autowired
    public QuotaService(AppUserRepository users, ProjectRepository projects, UsageEventRepository events) {
        this(users, projects, events, Clock.systemUTC());
    }

    QuotaService(AppUserRepository users, ProjectRepository projects, UsageEventRepository events, Clock clock) {
        this.users = users;
        this.projects = projects;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Usage usage(UUID userId) {
        AppUser u = users.findById(userId).orElseThrow();
        Plan plan = Plan.of(u.getPlan());
        Instant since = periodStart(u, plan);
        boolean free = plan == Plan.FREE;
        return new Usage(plan.slug(), exempt(u),
                new Meter(events.countByUserIdAndKindAndCreatedAtGreaterThanEqual(userId, UsageEvent.TAILOR, since), plan.tailors()),
                new Meter(free ? 0 : events.countByUserIdAndKindAndCreatedAtGreaterThanEqual(userId, UsageEvent.GENERATION, since), plan.generations()),
                free ? new Entries(events.countGeneratedEntries(userId, Project.Kind.PROJECT.name()), FREE_PROJECTS) : null,
                free ? new Entries(events.countGeneratedEntries(userId, Project.Kind.EXPERIENCE.name()), FREE_EXPERIENCES) : null,
                resetsAt(plan));
    }

    /** Refuses a new tailor when the plan has none left. */
    @Transactional(readOnly = true)
    public void checkTailor(UUID userId) {
        AppUser u = users.findById(userId).orElseThrow();
        if (exempt(u)) return;
        Plan plan = Plan.of(u.getPlan());
        long used = events.countByUserIdAndKindAndCreatedAtGreaterThanEqual(userId, UsageEvent.TAILOR, periodStart(u, plan));
        if (used >= plan.tailors()) {
            throw new QuotaExceededException(
                    "You have used all " + plan.tailors() + " tailors on the " + plan.slug() + " plan" + (plan.monthly() ? " for this month." : "."),
                    UsageEvent.TAILOR, plan, plan.tailors(), used, resetsAt(plan));
        }
    }

    @Transactional
    public void recordTailor(UUID userId, UUID applicationId) {
        events.save(new UsageEvent(userId, UsageEvent.TAILOR, applicationId));
    }

    /** Refuses a bullet-writing run the plan does not cover. Re-running an entry already written for is free on the free plan. */
    @Transactional(readOnly = true)
    public void checkGeneration(UUID userId, UUID projectId) {
        AppUser u = users.findById(userId).orElseThrow();
        if (exempt(u)) return;
        Plan plan = Plan.of(u.getPlan());
        if (plan == Plan.FREE) {
            checkFreeEntry(userId, projectId);
            return;
        }
        long used = events.countByUserIdAndKindAndCreatedAtGreaterThanEqual(userId, UsageEvent.GENERATION, periodStart(u, plan));
        if (used >= plan.generations()) {
            throw new QuotaExceededException(
                    "You have used all " + plan.generations() + " generations on the " + plan.slug() + " plan" + (plan.monthly() ? " for this month." : "."),
                    UsageEvent.GENERATION, plan, plan.generations(), used, resetsAt(plan));
        }
    }

    @Transactional
    public void recordGeneration(UUID userId, UUID projectId) {
        events.save(new UsageEvent(userId, UsageEvent.GENERATION, projectId));
    }

    private void checkFreeEntry(UUID userId, UUID projectId) {
        if (events.existsByUserIdAndKindAndRefId(userId, UsageEvent.GENERATION, projectId)) return;
        Project p = projects.findByUserIdAndId(userId, projectId).orElse(null);
        if (p == null) return;   // not theirs or gone: the service answers 404, not a paywall
        boolean experience = p.getKind() == Project.Kind.EXPERIENCE;
        int cap = experience ? FREE_EXPERIENCES : FREE_PROJECTS;
        long used = events.countGeneratedEntries(userId, p.getKind().name());
        if (used >= cap) {
            throw new QuotaExceededException(
                    "The free plan writes bullets for up to " + FREE_PROJECTS + " projects and " + FREE_EXPERIENCES + " work experience.",
                    UsageEvent.GENERATION, Plan.FREE, cap, used, null);
        }
    }

    private static boolean exempt(AppUser u) {
        return u.isAdmin() || u.isQuotaExempt();
    }

    /** Start of the current allowance: this calendar month (UTC), or for a pack the day it was bought. */
    private Instant periodStart(AppUser u, Plan plan) {
        if (plan.monthly()) return monthStart(clock.instant());
        return u.getPlanStartedAt() != null ? u.getPlanStartedAt() : Instant.EPOCH;
    }

    private Instant resetsAt(Plan plan) {
        return plan.monthly() ? monthStart(clock.instant()).atZone(ZoneOffset.UTC).plusMonths(1).toInstant() : null;
    }

    private static Instant monthStart(Instant now) {
        ZonedDateTime z = now.atZone(ZoneOffset.UTC);
        return z.withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
