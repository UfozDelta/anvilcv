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

/** Story grouping and the per-entry verb clash in selection, plus the story-aware variant collapse. */
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
    void sameVerbNeverRepeatsWithinAnEntryButMayAcrossEntries() {
        UUID pid1 = UUID.randomUUID(), pid2 = UUID.randomUUID();
        Project p1 = TestFixtures.project(pid1, Project.Kind.PROJECT, "A");
        Project p2 = TestFixtures.project(pid2, Project.Kind.PROJECT, "B");
        List<Bullet> bank = List.of(
                bullet(pid1, "Engineered a quote cache for the pricing API.", null),
                bullet(pid1, "Engineered retry handling for broker webhooks.", null),
                bullet(pid1, "Built tenant-scoped API keys.", null),
                bullet(pid1, "Wrote a replay harness from recorded market ticks.", null),
                bullet(pid2, "Engineered a CSV importer for brokerage statements.", null),
                bullet(pid2, "Built a tax-lot calculator for realized gains.", null),
                bullet(pid2, "Shipped a dividend calendar with email alerts.", null));
        List<Bullet> out = BulletSelector.select(ranked(bank),
                bank.stream().collect(Collectors.toMap(Bullet::getId, b -> b)),
                Map.of(pid1, p1, pid2, p2), bank, Set.of());

        assertFalse(out.contains(bank.get(1)), "second Engineered in entry A is skipped");
        assertTrue(out.contains(bank.get(4)), "Engineered may open a bullet in another entry");
        for (UUID pid : List.of(pid1, pid2)) {
            List<String> verbs = out.stream().filter(b -> b.getProjectId().equals(pid))
                    .map(b -> b.getText().split(" ")[0]).toList();
            assertEquals(verbs.size(), Set.copyOf(verbs).size(), "openers unique within entry");
        }
    }

    @Test
    void verbClashSwapsInAnotherWordingOfTheSameStory() {
        UUID pid = UUID.randomUUID();
        Project p = TestFixtures.project(pid, Project.Kind.PROJECT, "P");
        UUID story = UUID.randomUUID();
        Bullet c = bullet(pid, "Built a replay harness from recorded market ticks.", null);
        Bullet s1 = bullet(pid, "Built an order matcher that pairs fills across venues.", story);
        Bullet s2 = bullet(pid, "Designed venue-agnostic fill pairing with exact price keys.", story);
        Bullet d = bullet(pid, "Shipped a browser chart terminal for live order books.", null);

        assertEquals(List.of(c, s2, d), select(p, List.of(c, s1, s2, d)));
    }

    @Test
    void verbClashNeverStarvesAnEntryBelowTheFloor() {
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

        // Every bullet opens with "Built": the clash rule reorders, but the floor still fills both entries.
        assertEquals(6, out.size());
    }

    // ---- thin-entry swap ----

    /** Four PROJECT entries ranked onto the page (D holds one bullet), E only in the raw bank. */
    private record Page(Map<UUID, Project> projects, List<Bullet> bank, List<Bullet> ranked,
                        UUID a, UUID d, UUID e) {}

    private static Page page(int eBullets) {
        Map<UUID, Project> projects = new java.util.LinkedHashMap<>();
        List<Bullet> bank = new ArrayList<>(), ranked = new ArrayList<>();
        UUID[] ids = new UUID[5];
        for (int i = 0; i < 5; i++) {
            ids[i] = UUID.randomUUID();
            projects.put(ids[i], TestFixtures.project(ids[i], Project.Kind.PROJECT, "P" + i));
        }
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) ranked.add(TestFixtures.bullet(UUID.randomUUID(), ids[i], new String[0]));
        }
        ranked.add(TestFixtures.bullet(UUID.randomUUID(), ids[3], new String[0]));   // D: a one-bullet bank
        bank.addAll(ranked);
        for (int j = 0; j < eBullets; j++) bank.add(TestFixtures.bullet(UUID.randomUUID(), ids[4], new String[0]));
        return new Page(projects, bank, ranked, ids[0], ids[3], ids[4]);
    }

    private static List<Bullet> select(Page pg, List<Bullet> locked, UUID keep) {
        return BulletSelector.select(ranked(pg.ranked()),
                pg.ranked().stream().collect(Collectors.toMap(Bullet::getId, b -> b)),
                pg.projects(), pg.bank(), Set.of(), locked, Set.of(), keep);
    }

    private static long count(List<Bullet> out, UUID pid) {
        return out.stream().filter(b -> b.getProjectId().equals(pid)).count();
    }

    @Test
    void aThinEntryIsSwappedForTheNextBestEntryOfItsKind() {
        Page pg = page(3);

        List<Bullet> out = select(pg, List.of(), null);

        assertEquals(0, count(out, pg.d()), "the one-bullet entry is gone");
        assertEquals(3, count(out, pg.e()), "its replacement is topped up to three");
    }

    @Test
    void aThinEntryWithNoReplacementIsDroppedAboveTheFloor() {
        Page pg = page(1);   // E has too little left to replace D

        List<Bullet> out = select(pg, List.of(), null);

        assertEquals(0, count(out, pg.d()));
        assertEquals(0, count(out, pg.e()));
        assertEquals(9, out.size());
    }

    @Test
    void aThinEntryHoldingALockOrTheRefitTargetStays() {
        Page pg = page(3);
        Bullet dOnly = pg.ranked().get(9);

        assertEquals(1, count(select(pg, List.of(dOnly), null), pg.d()), "locked");
        assertEquals(1, count(select(pg, List.of(), pg.d()), pg.d()), "scoped refit target");
    }

    @Test
    void aThinEntryHoldingUpTheKindFloorStays() {
        Page pg = page(0);
        // Only D and two full entries of its kind: dropping D would break the PROJECT floor.
        Map<UUID, Project> three = new java.util.LinkedHashMap<>(pg.projects());
        three.remove(pg.a());
        three.remove(pg.e());
        List<Bullet> ranked = pg.ranked().stream().filter(b -> !b.getProjectId().equals(pg.a())).toList();
        Page small = new Page(three, ranked, ranked, null, pg.d(), null);

        List<Bullet> out = select(small, List.of(), null);

        assertEquals(1, count(out, pg.d()));
        assertEquals(7, out.size());
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
