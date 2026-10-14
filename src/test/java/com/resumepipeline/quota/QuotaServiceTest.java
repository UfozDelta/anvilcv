package com.resumepipeline.quota;

import com.resumepipeline.auth.AppUser;
import com.resumepipeline.auth.AppUserRepository;
import com.resumepipeline.project.Project;
import com.resumepipeline.project.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class QuotaServiceTest {

    static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");
    static final Instant MONTH_START = Instant.parse("2026-10-01T00:00:00Z");

    final UUID uid = UUID.randomUUID();
    final UUID pid = UUID.randomUUID();
    AppUserRepository users = mock(AppUserRepository.class);
    ProjectRepository projects = mock(ProjectRepository.class);
    UsageEventRepository events = mock(UsageEventRepository.class);
    QuotaService quota = new QuotaService(users, projects, events, Clock.fixed(NOW, ZoneOffset.UTC));
    AppUser user = new AppUser("u", "u@example.com", "x");

    @BeforeEach
    void setUp() {
        user.setQuotaExempt(false);
        when(users.findById(uid)).thenReturn(Optional.of(user));
    }

    private void tailorsUsed(long n) {
        when(events.countByUserIdAndKindAndCreatedAtGreaterThanEqual(eq(uid), eq(UsageEvent.TAILOR), any())).thenReturn(n);
    }

    private void generationsUsed(long n) {
        when(events.countByUserIdAndKindAndCreatedAtGreaterThanEqual(eq(uid), eq(UsageEvent.GENERATION), any())).thenReturn(n);
    }

    private Project entry(Project.Kind kind) {
        Project p = mock(Project.class);
        when(p.getKind()).thenReturn(kind);
        when(projects.findByUserIdAndId(uid, pid)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void freePlanAllowsThreeTailorsAMonthAndRefusesTheFourth() {
        tailorsUsed(2);
        assertDoesNotThrow(() -> quota.checkTailor(uid));

        tailorsUsed(3);
        QuotaExceededException e = assertThrows(QuotaExceededException.class, () -> quota.checkTailor(uid));
        assertEquals("tailor", e.kind());
        assertEquals(Plan.FREE, e.plan());
        assertEquals(3, e.limit());
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), e.resetsAt());
    }

    @Test
    void theMonthlyCountStartsOnTheFirstOfTheCalendarMonth() {
        tailorsUsed(0);
        quota.checkTailor(uid);
        verify(events).countByUserIdAndKindAndCreatedAtGreaterThanEqual(uid, UsageEvent.TAILOR, MONTH_START);
    }

    @Test
    void paidPlansHaveTheirOwnTailorLimits() {
        user.setPlan("starter");
        tailorsUsed(59);
        assertDoesNotThrow(() -> quota.checkTailor(uid));
        tailorsUsed(60);
        assertThrows(QuotaExceededException.class, () -> quota.checkTailor(uid));

        user.setPlan("pro");
        tailorsUsed(149);
        assertDoesNotThrow(() -> quota.checkTailor(uid));
        tailorsUsed(150);
        assertThrows(QuotaExceededException.class, () -> quota.checkTailor(uid));
    }

    @Test
    void aPackCountsFromTheDayItWasBoughtAndNeverResets() {
        Instant bought = Instant.parse("2026-09-03T08:00:00Z");
        user.setPlan("pack");
        user.setPlanStartedAt(bought);
        tailorsUsed(200);

        QuotaExceededException e = assertThrows(QuotaExceededException.class, () -> quota.checkTailor(uid));

        assertNull(e.resetsAt());
        verify(events).countByUserIdAndKindAndCreatedAtGreaterThanEqual(uid, UsageEvent.TAILOR, bought);
    }

    @Test
    void anUnknownPlanIsTreatedAsFree() {
        user.setPlan("enterprise-gold");
        tailorsUsed(3);
        assertThrows(QuotaExceededException.class, () -> quota.checkTailor(uid));
    }

    @Test
    void freePlanWritesBulletsForTwoProjectsAndOneExperience() {
        entry(Project.Kind.PROJECT);
        when(events.countGeneratedEntries(uid, "PROJECT")).thenReturn(1L);
        assertDoesNotThrow(() -> quota.checkGeneration(uid, pid));

        when(events.countGeneratedEntries(uid, "PROJECT")).thenReturn(2L);
        QuotaExceededException e = assertThrows(QuotaExceededException.class, () -> quota.checkGeneration(uid, pid));
        assertEquals(2, e.limit());
        assertNull(e.resetsAt());
    }

    @Test
    void freePlanExperienceCapIsOne() {
        entry(Project.Kind.EXPERIENCE);
        when(events.countGeneratedEntries(uid, "EXPERIENCE")).thenReturn(1L);
        QuotaExceededException e = assertThrows(QuotaExceededException.class, () -> quota.checkGeneration(uid, pid));
        assertEquals(1, e.limit());
    }

    @Test
    void rerunningAnEntryAlreadyWrittenForIsFreeOnTheFreePlan() {
        entry(Project.Kind.PROJECT);
        when(events.existsByUserIdAndKindAndRefId(uid, UsageEvent.GENERATION, pid)).thenReturn(true);
        when(events.countGeneratedEntries(uid, "PROJECT")).thenReturn(2L);
        assertDoesNotThrow(() -> quota.checkGeneration(uid, pid));
    }

    @Test
    void anUnknownProjectIsLeftForTheServiceToAnswer() {
        when(projects.findByUserIdAndId(uid, pid)).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> quota.checkGeneration(uid, pid));
    }

    @Test
    void paidPlansCapGenerationsPerPeriod() {
        user.setPlan("starter");
        generationsUsed(4);
        assertDoesNotThrow(() -> quota.checkGeneration(uid, pid));
        generationsUsed(5);
        QuotaExceededException e = assertThrows(QuotaExceededException.class, () -> quota.checkGeneration(uid, pid));
        assertEquals("generation", e.kind());
        assertEquals(5, e.limit());
    }

    @Test
    void exemptUsersAndAdminsAreNeverRefused() {
        tailorsUsed(1000);
        generationsUsed(1000);
        when(events.countGeneratedEntries(any(), any())).thenReturn(1000L);
        entry(Project.Kind.PROJECT);

        user.setQuotaExempt(true);
        assertDoesNotThrow(() -> quota.checkTailor(uid));
        assertDoesNotThrow(() -> quota.checkGeneration(uid, pid));

        user.setQuotaExempt(false);
        user.setAdmin(true);
        assertDoesNotThrow(() -> quota.checkTailor(uid));
        assertDoesNotThrow(() -> quota.checkGeneration(uid, pid));
    }

    @Test
    void recordingSavesAnEventForTheRightThing() {
        UUID app = UUID.randomUUID();
        quota.recordTailor(uid, app);
        quota.recordGeneration(uid, pid);

        var captor = org.mockito.ArgumentCaptor.forClass(UsageEvent.class);
        verify(events, times(2)).save(captor.capture());
        assertEquals(UsageEvent.TAILOR, captor.getAllValues().get(0).getKind());
        assertEquals(app, captor.getAllValues().get(0).getRefId());
        assertEquals(UsageEvent.GENERATION, captor.getAllValues().get(1).getKind());
        assertEquals(pid, captor.getAllValues().get(1).getRefId());
    }

    @Test
    void usageReportsWhatTheMeterNeeds() {
        tailorsUsed(2);
        when(events.countGeneratedEntries(uid, "PROJECT")).thenReturn(1L);
        when(events.countGeneratedEntries(uid, "EXPERIENCE")).thenReturn(0L);

        QuotaService.Usage u = quota.usage(uid);

        assertEquals("free", u.plan());
        assertEquals(2, u.tailors().used());
        assertEquals(3, u.tailors().limit());
        assertEquals(1, u.projects().used());
        assertEquals(2, u.projects().cap());
        assertEquals(1, u.experiences().cap());
        assertNull(u.generations().limit());
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), u.resetsAt());
    }
}
