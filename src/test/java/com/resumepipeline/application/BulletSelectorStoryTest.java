package com.resumepipeline.application;

import com.resumepipeline.bullet.Bullet;
import com.resumepipeline.llm.LlmClient;
import com.resumepipeline.project.Project;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Story grouping and the verb cap in selection, plus the story-aware variant collapse. */
class BulletSelectorStoryTest {

    private static Bullet bullet(UUID project, String text, UUID story) {
        Bullet b = TestFixtures.bullet(UUID.randomUUID(), project, new String[0]);
        b.setText(text);
        b.setStoryId(story);
        return b;
    }

    private static List<LlmClient.RankedBullet> ranked(List<Bullet> bs) {
        List<LlmClient.RankedBullet> r = new ArrayList<>();
        for (int i = 0; i < bs.size(); i++) r.add(TestFixtures.ranked(bs.get(i).getId(), i + 1));
        return r;
    }

    private static List<Bullet> select(Project p, List<Bullet> bank) {
        return BulletSelector.select(ranked(bank), bank.stream().collect(Collectors.toMap(Bullet::getId, b -> b)),
                Map.of(p.getId(), p), bank, Set.of());
    }

    @Test
    void twoWordingsOfOneStoryNeverShareAPage() {
        UUID pid = UUID.randomUUID();
        Project p = TestFixtures.project(pid, Project.Kind.PROJECT, "P");
        UUID story = UUID.randomUUID();
        Bullet a = bullet(pid, "Built an order matcher that pairs fills across venues.", story);
        Bullet aAlt = bullet(pid, "Designed venue-agnostic fill pairing with exact price keys.", story);
        Bullet c = bullet(pid, "Wrote a replay harness from recorded market ticks.", null);
        Bullet d = bullet(pid, "Shipped a browser chart terminal for live order books.", null);

        List<Bullet> out = select(p, List.of(a, aAlt, c, d));

        assertEquals(List.of(a, c, d), out);
    }

    @Test
    void legacyBulletsWithoutAStoryNeverClash() {
        UUID pid = UUID.randomUUID();
        Bullet a = bullet(pid, "Built x.", null);
        assertFalse(BulletSelector.sameStory(a, List.of(bullet(pid, "Built y.", null))));
    }

    @Test
    void verbCapSkipsAFourthOpenerWhenAnotherBulletCanFill() {
        UUID pid1 = UUID.randomUUID(), pid2 = UUID.randomUUID();
        Project p1 = TestFixtures.project(pid1, Project.Kind.PROJECT, "A");
        Project p2 = TestFixtures.project(pid2, Project.Kind.PROJECT, "B");
        List<Bullet> bank = List.of(
                bullet(pid1, "Engineered a quote cache for the pricing API.", null),
                bullet(pid1, "Engineered retry handling for broker webhooks.", null),
                bullet(pid1, "Engineered tenant-scoped API keys.", null),
                bullet(pid2, "Engineered a CSV importer for brokerage statements.", null),
                bullet(pid2, "Built a tax-lot calculator for realized gains.", null),
                bullet(pid2, "Shipped a dividend calendar with email alerts.", null),
                bullet(pid2, "Wrote a reconciliation job for broker balances.", null));
        List<Bullet> out = BulletSelector.select(ranked(bank),
                bank.stream().collect(Collectors.toMap(Bullet::getId, b -> b)),
                Map.of(pid1, p1, pid2, p2), bank, Set.of());

        long engineered = out.stream().filter(b -> b.getText().startsWith("Engineered")).count();
        assertEquals(BulletSelector.MAX_SAME_VERB, engineered);
        assertEquals(6, out.size());
        assertFalse(out.contains(bank.get(3)));
    }

    @Test
    void verbCapNeverStarvesAnEntryBelowTheFloor() {
        UUID pid = UUID.randomUUID();
        Project p = TestFixtures.project(pid, Project.Kind.PROJECT, "P");
        UUID other = UUID.randomUUID();
        Project q = TestFixtures.project(other, Project.Kind.PROJECT, "Q");
        List<Bullet> bank = new ArrayList<>();
        for (String t : List.of("Built a.", "Built b two.", "Built c three four.")) bank.add(bullet(pid, t, null));
        for (String t : List.of("Built d five six seven.", "Built e eight nine ten eleven.", "Built f twelve thirteen.")) {
            bank.add(bullet(other, t, null));
        }
        List<Bullet> out = BulletSelector.select(ranked(bank),
                bank.stream().collect(Collectors.toMap(Bullet::getId, b -> b)),
                Map.of(pid, p, other, q), bank, Set.of());

        // Every bullet opens with "Built": the cap reorders, but the floor still fills both entries.
        assertEquals(6, out.size());
    }

    @Test
    void collapseKeepsTheBestScoringWordingOfAStory() {
        UUID pid = UUID.randomUUID();
        UUID story = UUID.randomUUID();
        Bullet best = bullet(pid, "Built an order matcher that pairs fills across venues.", story);
        Bullet alt = bullet(pid, "Designed venue-agnostic fill pairing with exact price keys.", story);
        Bullet other = bullet(pid, "Wrote a replay harness from recorded market ticks.", null);

        List<Bullet> out = ApplicationService.collapseVariants(List.of(best, other, alt));

        assertEquals(List.of(best, other), out);
        assertTrue(BulletSelector.sameStory(alt, out));
    }
}
