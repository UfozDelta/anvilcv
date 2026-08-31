package com.resumepipeline.bullet;

import com.resumepipeline.config.GenerationConfig;
import com.resumepipeline.config.GenerationConfigService;
import com.resumepipeline.llm.BulletTextRules;
import com.resumepipeline.llm.LlmClient;
import com.resumepipeline.llm.LlmUsageService;
import com.resumepipeline.progress.ProgressLog;
import com.resumepipeline.project.Project;
import com.resumepipeline.project.ProjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The refit pass: which bullets get sent to the LLM, and which rewrites are allowed back in.
 * Every rejection path must leave the stored text exactly as it was — a refit that cannot
 * improve a bullet has to be a no-op, never a downgrade.
 */
@ExtendWith(MockitoExtension.class)
class BulletServiceRefitTest {

    @Mock BulletRepository repo;
    @Mock ProjectService projectService;
    @Mock LlmClient llm;
    @Mock LlmUsageService llmUsageService;
    @Mock GenerationConfigService configService;
    @InjectMocks BulletService service;

    private final UUID user = UUID.randomUUID();
    private final UUID proj = UUID.randomUUID();

    /** Default bands: 1 line = 81-97 chars, dead zone = 103-167, 2 lines = 173-200. */
    private static final GenerationConfig CFG = new GenerationConfig();

    /** In band (90 chars, one line). */
    private static final String IN_BAND =
            "Built a distributed ingestion service that cut nightly batch turnaround for the data team.";

    /** In the dead zone at 120 chars — the filter's "half-fills line 2" reject. */
    private static final String OFF_BAND =
            "Built a distributed ingestion service that cut the nightly batch turnaround time considerably for the whole data team.";

    /** A valid one-line rewrite of OFF_BAND, 88 chars, no number the original lacked. */
    private static final String GOOD_REWRITE =
            "Built a distributed ingestion service that cut nightly batch turnaround for the team.";

    /** 294 chars — three rendered lines, well past the two-line ceiling. */
    private static final String THREE_LINER =
            "Engineered a distributed ingestion service that replaced the nightly batch job for the "
          + "analytics team, adding backpressure, retry with jitter, and a dead-letter queue so a "
          + "slow downstream consumer could no longer stall the whole pipeline or silently drop "
          + "records during a partial outage window.";

    /** 201 chars — one over the ceiling, so still off-band, but two lines instead of three. */
    private static final String NEAR_MISS_REWRITE =
            "Engineered a distributed ingestion service replacing the nightly batch job, adding "
          + "backpressure, retry with jitter, and a dead-letter queue so a slow consumer cannot "
          + "stall the pipeline or drop records.";

    private Bullet bullet(String text) {
        Bullet b = new Bullet(proj, text, new String[]{"java"}, "backend");
        ReflectionTestUtils.setField(b, "id", UUID.randomUUID());
        return b;
    }

    private void given(Bullet... bullets) {
        when(projectService.get(user, proj))
                .thenReturn(new Project(user, Project.Kind.PROJECT, "P", "desc", null, "Eng", "Acme", "NYC", "2024"));
        when(configService.get(user)).thenReturn(CFG);
        when(repo.findByProjectIdOrderByCreatedAtAsc(proj)).thenReturn(List.of(bullets));
    }

    /** Reply the stubbed LLM sends back for the first (only) bullet it is asked to refit. */
    private void llmReplies(String text) {
        when(llm.refitBullets(any(), any(), any())).thenAnswer(inv -> {
            LlmClient.RefitRequest req = inv.getArgument(0);
            return new LlmClient.RefitResult(
                    List.of(new LlmClient.BulletToRefit(req.bullets().get(0).id(), text)));
        });
    }

    @Test
    void bandsUsedByTheTestAreTheOnesUnderTest() {
        // Guards the fixtures above: if the shipped defaults move, these strings stop
        // exercising the paths their names claim and the rest of the file quietly rots.
        assertEquals(BulletTextRules.Decision.KEPT,
                BulletTextRules.decide(BulletTextRules.charCount(IN_BAND), CFG));
        assertEquals(BulletTextRules.Decision.DEAD_ZONE,
                BulletTextRules.decide(BulletTextRules.charCount(OFF_BAND), CFG));
        assertEquals(BulletTextRules.Decision.KEPT,
                BulletTextRules.decide(BulletTextRules.charCount(GOOD_REWRITE), CFG));
    }

    @Test
    void skipsTheLlmEntirelyWhenEveryBulletAlreadyFits() {
        given(bullet(IN_BAND));

        BulletService.RefitOutcome out = service.refit(user, proj, ProgressLog.noOp());

        verifyNoInteractions(llm);
        verify(repo, never()).save(any());
        assertEquals(0, out.offBand());
        assertEquals(0, out.rewritten());
    }

    @Test
    void sendsOnlyTheOffBandBullets() {
        given(bullet(IN_BAND), bullet(OFF_BAND));
        llmReplies(GOOD_REWRITE);

        service.refit(user, proj, ProgressLog.noOp());

        var req = org.mockito.ArgumentCaptor.forClass(LlmClient.RefitRequest.class);
        verify(llm).refitBullets(req.capture(), any(), any());
        assertEquals(1, req.getValue().bullets().size());
        assertEquals(OFF_BAND, req.getValue().bullets().get(0).text());
    }

    @Test
    void neverTouchesAnApprovedBulletEvenWhenItIsOffBand() {
        Bullet approved = bullet(OFF_BAND);
        approved.setStatus("APPROVED");
        given(approved, bullet(IN_BAND));

        BulletService.RefitOutcome out = service.refit(user, proj, ProgressLog.noOp());

        verifyNoInteractions(llm);
        assertEquals(OFF_BAND, approved.getText());
        assertEquals(0, out.offBand());
        // Only the unapproved bullet was in scope.
        assertEquals(1, out.checked());
    }

    @Test
    void acceptsARewriteThatLandsInBand() {
        Bullet b = bullet(OFF_BAND);
        given(b);
        llmReplies(GOOD_REWRITE);

        BulletService.RefitOutcome out = service.refit(user, proj, ProgressLog.noOp());

        assertEquals(GOOD_REWRITE, b.getText());
        verify(repo).save(b);
        assertEquals(1, out.rewritten());
        assertEquals(0, out.unchanged());
    }

    @Test
    void rejectsARewriteThatInventsAMetric() {
        Bullet b = bullet(OFF_BAND);
        given(b);
        // In band at 89 chars, but "40%" appears nowhere in the original bullet.
        llmReplies("Built a distributed ingestion service that cut nightly batch turnaround by **40%**.");

        BulletService.RefitOutcome out = service.refit(user, proj, ProgressLog.noOp());

        assertEquals(OFF_BAND, b.getText(), "original must survive a fabricated-metric rewrite");
        verify(repo, never()).save(any());
        assertEquals(0, out.rewritten());
        assertEquals(1, out.unchanged());
    }

    @Test
    void rejectsARewriteThatIsStillOffBand() {
        Bullet b = bullet(OFF_BAND);
        given(b);
        llmReplies(OFF_BAND.replace("considerably", "substantially"));  // still in the dead zone

        service.refit(user, proj, ProgressLog.noOp());

        assertEquals(OFF_BAND, b.getText());
        verify(repo, never()).save(any());
    }

    @Test
    void acceptsAnOffBandRewriteThatCostsFewerLines() {
        // The point of the refit is page space. Rejecting a 201c/2-line rewrite reinstates a
        // 294c/3-line original, which is strictly worse by the measure that matters.
        Bullet b = bullet(THREE_LINER);
        given(b);
        llmReplies(NEAR_MISS_REWRITE);

        service.refit(user, proj, ProgressLog.noOp());

        assertNotEquals(BulletTextRules.Decision.KEPT,
                BulletTextRules.decide(BulletTextRules.charCount(NEAR_MISS_REWRITE), CFG),
                "fixture must still be off-band, or this asserts nothing");
        assertEquals(NEAR_MISS_REWRITE, b.getText());
        verify(repo).save(b);
    }

    @Test
    void stillRejectsAnOffBandRewriteThatSavesNoLines() {
        Bullet b = bullet(THREE_LINER);
        given(b);
        llmReplies(THREE_LINER.replace("silently drop", "quietly drop"));

        service.refit(user, proj, ProgressLog.noOp());

        assertEquals(THREE_LINER, b.getText());
        verify(repo, never()).save(any());
    }

    @Test
    void rejectsARewriteThatOpensWeakly() {
        Bullet b = bullet(OFF_BAND);
        given(b);
        llmReplies("Worked on a distributed ingestion service that cut the nightly batch turnaround.");

        service.refit(user, proj, ProgressLog.noOp());

        assertEquals(OFF_BAND, b.getText());
        verify(repo, never()).save(any());
    }

    @Test
    void ignoresARewriteCarryingAnIdItWasNeverGiven() {
        Bullet b = bullet(OFF_BAND);
        given(b);
        when(llm.refitBullets(any(), any(), any())).thenReturn(new LlmClient.RefitResult(
                List.of(new LlmClient.BulletToRefit(UUID.randomUUID().toString(), GOOD_REWRITE))));

        service.refit(user, proj, ProgressLog.noOp());

        assertEquals(OFF_BAND, b.getText(), "a reply must never be applied to a bullet it does not name");
        verify(repo, never()).save(any());
    }
}
