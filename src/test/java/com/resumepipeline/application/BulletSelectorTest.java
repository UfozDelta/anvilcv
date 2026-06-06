package com.resumepipeline.application;

import com.resumepipeline.bullet.Bullet;
import com.resumepipeline.llm.LlmClient;
import com.resumepipeline.project.Project;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class BulletSelectorTest {

    private static final Set<String> NO_KEYWORDS = Set.of();

    // ---- helpers ----

    private static Map<UUID, Bullet> byId(List<Bullet> bullets) {
        return bullets.stream().collect(Collectors.toMap(Bullet::getId, b -> b));
    }

    private static Map<UUID, Project> projectsById(Project... ps) {
        return Arrays.stream(ps).collect(Collectors.toMap(Project::getId, p -> p));
    }

    private static List<UUID> ids(List<Bullet> bullets) {
        return bullets.stream().map(Bullet::getId).toList();
    }

    @Nested
    class Select {

        @Test
        void greedyKeepsRankOrder() {
            UUID proj = UUID.randomUUID();
            Project p = TestFixtures.project(proj, Project.Kind.PROJECT, "P");
            Bullet b1 = TestFixtures.bullet(UUID.randomUUID(), proj, new String[0]);
            Bullet b2 = TestFixtures.bullet(UUID.randomUUID(), proj, new String[0]);
            // ranked best-first: b2 then b1
            List<LlmClient.RankedBullet> ranked = List.of(
                    TestFixtures.ranked(b2.getId(), 1),
                    TestFixtures.ranked(b1.getId(), 2));

            List<Bullet> out = BulletSelector.select(ranked, byId(List.of(b1, b2)),
                    projectsById(p), List.of(b1, b2), NO_KEYWORDS);

            assertEquals(List.of(b2.getId(), b1.getId()), ids(out));
        }

        @Test
        void perProjectCapEnforced() {
            UUID proj = UUID.randomUUID();
            Project p = TestFixtures.project(proj, Project.Kind.PROJECT, "P");
            // 5 bullets, all same project — cap is 3.
            List<Bullet> bullets = IntStream.range(0, 5)
                    .mapToObj(i -> TestFixtures.bullet(UUID.randomUUID(), proj, new String[0]))
                    .toList();
            List<LlmClient.RankedBullet> ranked = IntStream.range(0, 5)
                    .mapToObj(i -> TestFixtures.ranked(bullets.get(i).getId(), i + 1))
                    .toList();

            List<Bullet> out = BulletSelector.select(ranked, byId(bullets),
                    projectsById(p), bullets, NO_KEYWORDS);

            assertEquals(BulletSelector.MAX_PER_PROJECT, out.size());
        }

        @Test
        void totalCapEnforced() {
            // 20 distinct projects, 1 bullet each -> only MAX_TOTAL survive greedy.
            List<Project> projects = new ArrayList<>();
            List<Bullet> bullets = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                UUID pid = UUID.randomUUID();
                projects.add(TestFixtures.project(pid, Project.Kind.PROJECT, "P" + i));
                bullets.add(TestFixtures.bullet(UUID.randomUUID(), pid, new String[0]));
            }
            List<LlmClient.RankedBullet> ranked = IntStream.range(0, 20)
                    .mapToObj(i -> TestFixtures.ranked(bullets.get(i).getId(), i + 1))
                    .toList();
            Map<UUID, Project> projById = projects.stream()
                    .collect(Collectors.toMap(Project::getId, p -> p));

            List<Bullet> out = BulletSelector.select(ranked, byId(bullets), projById, bullets, NO_KEYWORDS);

            assertEquals(BulletSelector.MAX_TOTAL, out.size());
        }

        @Test
        void skipsMalformedBulletId() {
            UUID proj = UUID.randomUUID();
            Project p = TestFixtures.project(proj, Project.Kind.PROJECT, "P");
            Bullet b = TestFixtures.bullet(UUID.randomUUID(), proj, new String[0]);
            List<LlmClient.RankedBullet> ranked = List.of(
                    TestFixtures.rankedRaw("not-a-uuid", 1),
                    TestFixtures.ranked(b.getId(), 2));

            List<Bullet> out = BulletSelector.select(ranked, byId(List.of(b)),
                    projectsById(p), List.of(b), NO_KEYWORDS);

            assertEquals(List.of(b.getId()), ids(out));
        }

        @Test
        void skipsRankedIdNotInCandidates() {
            UUID proj = UUID.randomUUID();
            Project p = TestFixtures.project(proj, Project.Kind.PROJECT, "P");
            Bullet b = TestFixtures.bullet(UUID.randomUUID(), proj, new String[0]);
            UUID ghost = UUID.randomUUID(); // ranked but never a candidate
            List<LlmClient.RankedBullet> ranked = List.of(
                    TestFixtures.ranked(ghost, 1),
                    TestFixtures.ranked(b.getId(), 2));

            List<Bullet> out = BulletSelector.select(ranked, byId(List.of(b)),
                    projectsById(p), List.of(b), NO_KEYWORDS);

            assertEquals(List.of(b.getId()), ids(out));
        }

        @Test
        void kindFloorForcesExperienceDiversity() {
            // Greedy fills 3 PROJECT entries (cap-less here, distinct projects) but zero EXPERIENCE.
            // Kind floor must pull in EXPERIENCE projects (min 2).
            UUID projA = UUID.randomUUID(), projB = UUID.randomUUID(), projC = UUID.randomUUID();
            UUID expA = UUID.randomUUID(), expB = UUID.randomUUID();
            Project pA = TestFixtures.project(projA, Project.Kind.PROJECT, "A");
            Project pB = TestFixtures.project(projB, Project.Kind.PROJECT, "B");
            Project pC = TestFixtures.project(projC, Project.Kind.PROJECT, "C");
            Project eA = TestFixtures.project(expA, Project.Kind.EXPERIENCE, "ExpA");
            Project eB = TestFixtures.project(expB, Project.Kind.EXPERIENCE, "ExpB");

            Bullet ba = TestFixtures.bullet(UUID.randomUUID(), projA, new String[0]);
            Bullet bb = TestFixtures.bullet(UUID.randomUUID(), projB, new String[0]);
            Bullet bc = TestFixtures.bullet(UUID.randomUUID(), projC, new String[0]);
            Bullet bea = TestFixtures.bullet(UUID.randomUUID(), expA, new String[0]);
            Bullet beb = TestFixtures.bullet(UUID.randomUUID(), expB, new String[0]);

            List<Bullet> all = List.of(ba, bb, bc, bea, beb);
            // Experience bullets ranked worst so greedy ignores them first.
            List<LlmClient.RankedBullet> ranked = List.of(
                    TestFixtures.ranked(ba.getId(), 1),
                    TestFixtures.ranked(bb.getId(), 2),
                    TestFixtures.ranked(bc.getId(), 3),
                    TestFixtures.ranked(bea.getId(), 4),
                    TestFixtures.ranked(beb.getId(), 5));

            List<Bullet> out = BulletSelector.select(ranked, byId(all),
                    projectsById(pA, pB, pC, eA, eB), all, NO_KEYWORDS);

            Set<UUID> outProjects = out.stream().map(Bullet::getProjectId).collect(Collectors.toSet());
            assertTrue(outProjects.contains(expA), "expA pulled in by kind floor");
            assertTrue(outProjects.contains(expB), "expB pulled in by kind floor");
        }

        @Test
        void minFillPadsFromBankWhenNotRanked() {
            // One PROJECT with 3 bullets, but only 1 is ranked. Min-fill should pad the
            // other 2 from the raw bank (source 2) up to MAX_PER_PROJECT.
            UUID proj = UUID.randomUUID();
            Project p = TestFixtures.project(proj, Project.Kind.PROJECT, "P");
            Bullet ranked1 = TestFixtures.bullet(UUID.randomUUID(), proj, new String[0]);
            Bullet bank1 = TestFixtures.bullet(UUID.randomUUID(), proj, new String[0]);
            Bullet bank2 = TestFixtures.bullet(UUID.randomUUID(), proj, new String[0]);
            List<Bullet> all = List.of(ranked1, bank1, bank2);

            // Only ranked1 is a candidate / ranked.
            List<LlmClient.RankedBullet> ranked = List.of(TestFixtures.ranked(ranked1.getId(), 1));

            List<Bullet> out = BulletSelector.select(ranked, byId(List.of(ranked1)),
                    projectsById(p), all, NO_KEYWORDS);

            assertEquals(3, out.size());
            assertTrue(ids(out).contains(bank1.getId()));
            assertTrue(ids(out).contains(bank2.getId()));
        }

        @Test
        void minFillBankFallbackOrdersByTagScore() {
            UUID proj = UUID.randomUUID();
            Project p = TestFixtures.project(proj, Project.Kind.PROJECT, "P");
            Bullet ranked1 = TestFixtures.bullet(UUID.randomUUID(), proj, new String[0]);
            Bullet lowTag = TestFixtures.bullet(UUID.randomUUID(), proj, new String[]{"misc"});
            Bullet highTag = TestFixtures.bullet(UUID.randomUUID(), proj, new String[]{"java", "spring"});
            List<Bullet> all = List.of(ranked1, lowTag, highTag);

            List<LlmClient.RankedBullet> ranked = List.of(TestFixtures.ranked(ranked1.getId(), 1));
            Set<String> kw = Set.of("java", "spring");

            List<Bullet> out = BulletSelector.select(ranked, byId(List.of(ranked1)),
                    projectsById(p), all, kw);

            // ranked1 first, then highTag (score 2) before lowTag (score 0).
            assertEquals(List.of(ranked1.getId(), highTag.getId(), lowTag.getId()), ids(out));
        }
    }

    @Nested
    class FillSkills {

        @Test
        void padsUpToFloorFromRaw() {
            Map<String, List<String>> selected = Map.of("languages", List.of("Java"));
            Map<String, List<String>> raw = Map.of(
                    "languages", List.of("Java", "Python", "Go", "Rust", "C", "C++", "Kotlin"),
                    "frameworks", List.of(), "databases", List.of(), "devops", List.of());

            Map<String, List<String>> out = BulletSelector.fillSkills(selected, raw);

            assertEquals(BulletSelector.MIN_SKILLS_PER_CATEGORY, out.get("languages").size());
            assertEquals("Java", out.get("languages").get(0), "selected item kept first");
        }

        @Test
        void deduplicatesAcrossSelectedAndRaw() {
            Map<String, List<String>> selected = Map.of("languages", List.of("Java", "Python"));
            Map<String, List<String>> raw = Map.of(
                    "languages", List.of("Java", "Python", "Go"), // Java/Python already present
                    "frameworks", List.of(), "databases", List.of(), "devops", List.of());

            Map<String, List<String>> out = BulletSelector.fillSkills(selected, raw);

            assertEquals(List.of("Java", "Python", "Go"), out.get("languages"));
        }

        @Test
        void allFourCategoriesPresent() {
            Map<String, List<String>> out = BulletSelector.fillSkills(Map.of(), Map.of());
            for (String key : BulletSelector.SKILL_KEYS) {
                assertNotNull(out.get(key), key + " present");
                assertTrue(out.get(key).isEmpty());
            }
        }

        @Test
        void nullSelectedTreatedAsEmpty() {
            Map<String, List<String>> raw = Map.of(
                    "languages", List.of("Java", "Go"),
                    "frameworks", List.of(), "databases", List.of(), "devops", List.of());
            Map<String, List<String>> out = BulletSelector.fillSkills(null, raw);
            assertEquals(List.of("Java", "Go"), out.get("languages"));
        }

        @Test
        void doesNotTrimWhenSelectedExceedsFloor() {
            List<String> eight = List.of("a", "b", "c", "d", "e", "f", "g", "h");
            Map<String, List<String>> selected = Map.of("languages", eight);
            Map<String, List<String>> raw = Map.of("languages", List.of("x"),
                    "frameworks", List.of(), "databases", List.of(), "devops", List.of());

            Map<String, List<String>> out = BulletSelector.fillSkills(selected, raw);

            assertEquals(eight, out.get("languages"), "over-floor selection left intact, no raw padding");
        }
    }
}
