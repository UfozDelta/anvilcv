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
        void entryCapBoundsProjectCountAndTotal() {
            // 20 candidate projects, each with a full 3-bullet bank. Only MAX_ENTRIES of them
            // may open, and each opened one is filled to MAX_PER_PROJECT -- so the selection is
            // exactly MAX_TOTAL bullets spread over MAX_ENTRIES entries, never a long tail of
            // thin ones. Before the entry cap existed, greedy opened all 20.
            List<Project> projects = new ArrayList<>();
            List<Bullet> ranked1 = new ArrayList<>();
            List<Bullet> all = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                UUID pid = UUID.randomUUID();
                projects.add(TestFixtures.project(pid, Project.Kind.PROJECT, "P" + i));
                Bullet r = TestFixtures.bullet(UUID.randomUUID(), pid, new String[0]);
                ranked1.add(r);
                all.add(r);
                all.add(TestFixtures.bullet(UUID.randomUUID(), pid, new String[0]));
                all.add(TestFixtures.bullet(UUID.randomUUID(), pid, new String[0]));
            }
            List<LlmClient.RankedBullet> ranked = IntStream.range(0, 20)
                    .mapToObj(i -> TestFixtures.ranked(ranked1.get(i).getId(), i + 1))
                    .toList();
            Map<UUID, Project> projById = projects.stream()
                    .collect(Collectors.toMap(Project::getId, p -> p));

            List<Bullet> out = BulletSelector.select(ranked, byId(ranked1), projById, all, NO_KEYWORDS);

            assertEquals(BulletSelector.MAX_ENTRIES,
                    out.stream().map(Bullet::getProjectId).distinct().count());
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
        void minFillCannotExceedTotalCap() {
            // 8 PROJECT entries, 1 ranked bullet each (pass 1 takes all 8) plus 2 spare
            // bank bullets each. Unguarded min-fill would pad to 8 * 3 = 24.
            List<Project> projects = new ArrayList<>();
            List<Bullet> rankedBullets = new ArrayList<>();
            List<Bullet> all = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                UUID pid = UUID.randomUUID();
                projects.add(TestFixtures.project(pid, Project.Kind.PROJECT, "P" + i));
                Bullet r = TestFixtures.bullet(UUID.randomUUID(), pid, new String[0]);
                rankedBullets.add(r);
                all.add(r);
                all.add(TestFixtures.bullet(UUID.randomUUID(), pid, new String[0]));
                all.add(TestFixtures.bullet(UUID.randomUUID(), pid, new String[0]));
            }
            List<LlmClient.RankedBullet> ranked = IntStream.range(0, 8)
                    .mapToObj(i -> TestFixtures.ranked(rankedBullets.get(i).getId(), i + 1))
                    .toList();
            Map<UUID, Project> projById = projects.stream()
                    .collect(Collectors.toMap(Project::getId, p -> p));

            List<Bullet> out = BulletSelector.select(ranked, byId(rankedBullets), projById, all, NO_KEYWORDS);

            assertEquals(BulletSelector.MAX_TOTAL, out.size());
            assertEquals(out.size(), new HashSet<>(ids(out)).size(), "no duplicates");
        }

        @Test
        void kindFloorCannotExceedTotalCapAndStillMeetsFloor() {
            // Pass 1 saturates on PROJECT entries alone: 5 projects * 3 bullets = 15 = MAX_TOTAL,
            // leaving zero EXPERIENCE entries. Pass 2 must still reach MIN_EXPERIENCE_PROJECTS,
            // and must do it by evicting rather than by growing past the cap.
            List<Project> projects = new ArrayList<>();
            List<Bullet> rankedBullets = new ArrayList<>();
            List<Bullet> all = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                UUID pid = UUID.randomUUID();
                projects.add(TestFixtures.project(pid, Project.Kind.PROJECT, "P" + i));
                for (int j = 0; j < 3; j++) {
                    Bullet b = TestFixtures.bullet(UUID.randomUUID(), pid, new String[0]);
                    rankedBullets.add(b);
                    all.add(b);
                }
            }
            // Two EXPERIENCE entries ranked strictly below every project bullet, so only pass 2
            // can pull them in.
            for (int i = 0; i < 2; i++) {
                UUID pid = UUID.randomUUID();
                projects.add(TestFixtures.project(pid, Project.Kind.EXPERIENCE, "E" + i));
                Bullet b = TestFixtures.bullet(UUID.randomUUID(), pid, new String[0]);
                rankedBullets.add(b);
                all.add(b);
            }
            List<LlmClient.RankedBullet> ranked = IntStream.range(0, rankedBullets.size())
                    .mapToObj(i -> TestFixtures.ranked(rankedBullets.get(i).getId(), i + 1))
                    .toList();
            Map<UUID, Project> projById = projects.stream()
                    .collect(Collectors.toMap(Project::getId, p -> p));

            List<Bullet> out = BulletSelector.select(ranked, byId(rankedBullets), projById, all, NO_KEYWORDS);

            assertTrue(out.size() <= BulletSelector.MAX_TOTAL,
                    "kind-floor must not push the selection past the cap, got " + out.size());
            assertEquals(out.size(), new HashSet<>(ids(out)).size(), "no duplicates");
            long expProjects = out.stream().map(Bullet::getProjectId).distinct()
                    .filter(pid -> projById.get(pid).getKind() == Project.Kind.EXPERIENCE)
                    .count();
            assertTrue(expProjects >= 2, "experience floor still met, got " + expProjects);
        }

        @Test
        void minFillStillPadsFullyWhenUnderTotalCap() {
            // 3 projects * 3 bullets = 9 <= MAX_TOTAL, so the cap guard must not fire.
            List<Project> projects = new ArrayList<>();
            List<Bullet> rankedBullets = new ArrayList<>();
            List<Bullet> all = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                UUID pid = UUID.randomUUID();
                projects.add(TestFixtures.project(pid, Project.Kind.PROJECT, "P" + i));
                Bullet r = TestFixtures.bullet(UUID.randomUUID(), pid, new String[0]);
                rankedBullets.add(r);
                all.add(r);
                all.add(TestFixtures.bullet(UUID.randomUUID(), pid, new String[0]));
                all.add(TestFixtures.bullet(UUID.randomUUID(), pid, new String[0]));
            }
            List<LlmClient.RankedBullet> ranked = IntStream.range(0, 3)
                    .mapToObj(i -> TestFixtures.ranked(rankedBullets.get(i).getId(), i + 1))
                    .toList();
            Map<UUID, Project> projById = projects.stream()
                    .collect(Collectors.toMap(Project::getId, p -> p));

            List<Bullet> out = BulletSelector.select(ranked, byId(rankedBullets), projById, all, NO_KEYWORDS);

            assertEquals(9, out.size());
            for (Project p : projects) {
                assertEquals(BulletSelector.MAX_PER_PROJECT,
                        out.stream().filter(b -> b.getProjectId().equals(p.getId())).count(),
                        p.getName() + " padded to the per-project cap");
            }
        }

        @Test
        void lineBudgetLimitsLongBulletSelectionBelowMaxTotal() {
            // 20 distinct projects, 1 bullet each, but every bullet is a ~320-char / 4-line
            // monster. MAX_TOTAL_LINES = 26 must bind well before MAX_TOTAL = 15 bullets.
            List<Project> projects = new ArrayList<>();
            List<Bullet> bullets = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                UUID pid = UUID.randomUUID();
                projects.add(TestFixtures.project(pid, Project.Kind.PROJECT, "P" + i));
                Bullet b = TestFixtures.bullet(UUID.randomUUID(), pid, new String[0]);
                b.setText("x".repeat(320));
                bullets.add(b);
            }
            List<LlmClient.RankedBullet> ranked = IntStream.range(0, 20)
                    .mapToObj(i -> TestFixtures.ranked(bullets.get(i).getId(), i + 1))
                    .toList();
            Map<UUID, Project> projById = projects.stream()
                    .collect(Collectors.toMap(Project::getId, p -> p));

            List<Bullet> out = BulletSelector.select(ranked, byId(bullets), projById, bullets, NO_KEYWORDS);

            assertTrue(out.size() < BulletSelector.MAX_TOTAL,
                    "line budget should bind before the bullet-count cap, got " + out.size());
        }


        @Test
        void nearDuplicateBulletIsRejectedEvenWhenBothRankedHighest() {
            // Modeled on the real shipped-PDF pair (same backtester claim, reworded, same
            // numbers), but reworded closer to verbatim than the original evidence: the
            // literal shipped text (see class javadoc / task writeup) actually scores 0.386
            // Jaccard under BulletTextRules.similarity — BELOW the 0.6 threshold — because the
            // two bullets emphasize different facets (Sharpe/period vs. universe/tickers) and
            // share under half their vocabulary. That real pair is a case the word-overlap
            // metric genuinely misses; fixing that would mean changing the similarity metric
            // itself, out of scope here (task said not to touch NEAR_DUPLICATE_THRESHOLD or
            // reimplement BulletTextRules). This fixture instead uses a paraphrase-level
            // near-duplicate (0.86 Jaccard) to prove the wiring — isNearDuplicate is actually
            // consulted by pass 1 — works correctly.
            UUID proj = UUID.randomUUID();
            Project p = TestFixtures.project(proj, Project.Kind.PROJECT, "P");
            Bullet first = TestFixtures.bullet(UUID.randomUUID(), proj, new String[0]);
            first.setText("Engineered a full backtesting infrastructure for a systematic equity "
                    + "index, comprising 6,062 lines of Python across 9 modules and 14 classes, "
                    + "leveraging pandas and numpy, yielding 46.44% cumulative return and a "
                    + "Sharpe 2.976 in backtest over a 10-month period, outperforming benchmarks.");
            Bullet second = TestFixtures.bullet(UUID.randomUUID(), proj, new String[0]);
            second.setText("Architected a full backtesting infrastructure for a systematic "
                    + "equity index, comprising 6,062 lines of Python across 9 modules and 14 "
                    + "classes, leveraging pandas and numpy, delivering 46.44% cumulative return "
                    + "and a Sharpe 2.976 in backtest over a 10-month period, beating benchmarks.");
            List<Bullet> all = List.of(first, second);
            List<LlmClient.RankedBullet> ranked = List.of(
                    TestFixtures.ranked(first.getId(), 1),
                    TestFixtures.ranked(second.getId(), 2));

            List<Bullet> out = BulletSelector.select(ranked, byId(all),
                    projectsById(p), all, NO_KEYWORDS);

            assertEquals(1, out.size(), "only one of the near-duplicate pair survives");
            assertEquals(first.getId(), out.get(0).getId(), "higher-ranked one wins");
        }

        @Test
        void merelySimilarTopicBelowThresholdIsNotDropped() {
            // Same project domain, same rough shape, but different content/numbers —
            // Jaccard overlap should land well under the 0.6 threshold.
            UUID proj = UUID.randomUUID();
            Project p = TestFixtures.project(proj, Project.Kind.PROJECT, "P");
            Bullet first = TestFixtures.bullet(UUID.randomUUID(), proj, new String[0]);
            first.setText("Built a REST API in Spring Boot handling 500 requests per second "
                    + "with Redis caching and PostgreSQL persistence.");
            Bullet second = TestFixtures.bullet(UUID.randomUUID(), proj, new String[0]);
            second.setText("Wrote a CI/CD pipeline in GitHub Actions that cut deploy time from "
                    + "40 minutes to 8 minutes across three microservices.");
            List<Bullet> all = List.of(first, second);
            List<LlmClient.RankedBullet> ranked = List.of(
                    TestFixtures.ranked(first.getId(), 1),
                    TestFixtures.ranked(second.getId(), 2));

            List<Bullet> out = BulletSelector.select(ranked, byId(all),
                    projectsById(p), all, NO_KEYWORDS);

            assertEquals(2, out.size(), "unrelated bullets both kept");
        }

        @Test
        void evictedBulletStopsBlockingLaterNearDuplicateCandidate() {
            // Force pass 2's eviction path (see evictWeakestFromFullestProject) to remove a
            // specific bullet (t3) via the LINE budget rather than the total-count cap: the
            // total-count cap makes every eviction immediately followed by an add of equal
            // count, which never leaves pass 3 (min-fill) any room to run. Line-budget eviction
            // can instead evict-then-still-not-fit (see the "move on to the next candidate"
            // comment in BulletSelector pass 2), permanently dropping the count by one and
            // leaving min-fill free to pad the target project back up from the bank.
            //
            // Layout: 4 filler PROJECT entries (1 short bullet each) + 1 target PROJECT with 3
            // short bullets (t1, t2, t3) = 7 bullets / 7 lines, well under MAX_TOTAL and
            // MAX_TOTAL_LINES. Four fillers, not six: MAX_ENTRIES caps how many projects greedy
            // may open, and the target has to be one of them for it to be the eviction victim.
            // The target is uniquely "fullest" (count 3, fillers count 1 each), so eviction
            // always picks its worst-ranked bullet: t3.
            //
            // exp1 is an oversized (~39-line) EXPERIENCE candidate: adding it would blow the
            // line budget even after evicting t3, so it gets evicted-and-skipped, permanently
            // freeing t3's slot. exp2 is a normal-sized EXPERIENCE candidate that then fits
            // directly. Finally, bankNearDup — a near-duplicate of t3's text, offered only
            // through the min-fill bank fallback (never ranked) — must be admitted for the
            // target project once t3's text is no longer in the "already selected" set.
            //
            // This also pins the pass-4 side of the same rule: the floor pass matches on the
            // LIVE selection rather than on selectedIds, so t3 is eligible again — but the
            // target is already back to MAX_PER_PROJECT via bankNearDup, so t3 stays out.
            List<Project> projects = new ArrayList<>();
            List<Bullet> rankedBullets = new ArrayList<>();
            List<Bullet> all = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                UUID pid = UUID.randomUUID();
                projects.add(TestFixtures.project(pid, Project.Kind.PROJECT, "Filler" + i));
                Bullet b = TestFixtures.bullet(UUID.randomUUID(), pid, new String[0]);
                rankedBullets.add(b);
                all.add(b);
            }
            UUID targetProjId = UUID.randomUUID();
            Project targetProj = TestFixtures.project(targetProjId, Project.Kind.PROJECT, "Target");
            projects.add(targetProj);
            Bullet t1 = TestFixtures.bullet(UUID.randomUUID(), targetProjId, new String[0]);
            Bullet t2 = TestFixtures.bullet(UUID.randomUUID(), targetProjId, new String[0]);
            Bullet t3 = TestFixtures.bullet(UUID.randomUUID(), targetProjId, new String[0]);
            t3.setText("Built a data pipeline processing 10000 records daily using Kafka and "
                    + "Spark for real time analytics.");
            rankedBullets.add(t1); rankedBullets.add(t2); rankedBullets.add(t3);
            all.add(t1); all.add(t2); all.add(t3);

            // Two EXPERIENCE entries, ranked worse than everything above, to force pass 2.
            UUID exp1 = UUID.randomUUID(), exp2 = UUID.randomUUID();
            projects.add(TestFixtures.project(exp1, Project.Kind.EXPERIENCE, "E1"));
            projects.add(TestFixtures.project(exp2, Project.Kind.EXPERIENCE, "E2"));
            Bullet e1 = TestFixtures.bullet(UUID.randomUUID(), exp1, new String[0]);
            e1.setText("x".repeat(4000)); // ~39 lines — cannot fit even after one eviction
            Bullet e2 = TestFixtures.bullet(UUID.randomUUID(), exp2, new String[0]);
            rankedBullets.add(e1); rankedBullets.add(e2);
            all.add(e1); all.add(e2);

            // Bank-only bullet for the target project, near-duplicate of t3's text, offered
            // only through min-fill (never ranked).
            Bullet bankNearDup = TestFixtures.bullet(UUID.randomUUID(), targetProjId, new String[0]);
            bankNearDup.setText("Built a data pipeline processing 10000 records daily using "
                    + "Kafka and Spark for real time reporting.");
            all.add(bankNearDup);

            List<LlmClient.RankedBullet> ranked = IntStream.range(0, rankedBullets.size())
                    .mapToObj(i -> TestFixtures.ranked(rankedBullets.get(i).getId(), i + 1))
                    .toList();
            Map<UUID, Project> projById = projects.stream()
                    .collect(Collectors.toMap(Project::getId, p -> p));

            List<Bullet> out = BulletSelector.select(ranked, byId(rankedBullets), projById, all, NO_KEYWORDS);

            assertFalse(ids(out).contains(t3.getId()), "t3 (weakest of the uniquely-fullest target) was evicted");
            assertFalse(ids(out).contains(e1.getId()), "oversized e1 never fit, even after evicting t3");
            assertTrue(ids(out).contains(bankNearDup.getId()),
                    "bank near-duplicate of the evicted bullet is admitted once the evicted text is gone");
            assertTrue(out.size() <= BulletSelector.MAX_TOTAL);
        }

        /**
         * The invariant itself, asserted directly rather than inferred from a bullet count:
         * every project that survives selection carries MAX_PER_PROJECT bullets, or its whole
         * dedup-admissible bank when that bank holds fewer. No entry is ever a stub.
         */
        private static void assertNoStubEntries(List<Bullet> out, List<Bullet> all) {
            Map<UUID, List<Bullet>> bank = all.stream()
                    .collect(Collectors.groupingBy(Bullet::getProjectId));
            for (UUID pid : out.stream().map(Bullet::getProjectId).distinct().toList()) {
                long have = out.stream().filter(b -> b.getProjectId().equals(pid)).count();
                int available = bank.getOrDefault(pid, List.of()).size();
                assertEquals(Math.min(BulletSelector.MAX_PER_PROJECT, available), have,
                        "project " + pid + " must carry min(MAX_PER_PROJECT, bank), got " + have
                                + " of a " + available + "-bullet bank");
            }
        }

        @Test
        void everySurvivingProjectIsFilledToTheFloorUnderLineBudgetPressure() {
            // The shipped-PDF shape: more projects than the page can hold, every bullet a
            // 2-line ~180-char bullet, so the line budget binds hard. Before the floor pass,
            // greedy spent the page on the top entries and left the tail at one bullet each.
            // Now the page is spent in whole entries and no stub survives.
            List<Project> projects = new ArrayList<>();
            List<Bullet> rankedBullets = new ArrayList<>();
            List<Bullet> all = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                UUID pid = UUID.randomUUID();
                projects.add(TestFixtures.project(pid, Project.Kind.PROJECT, "P" + i));
                for (int j = 0; j < 3; j++) {
                    Bullet b = TestFixtures.bullet(UUID.randomUUID(), pid, new String[0]);
                    b.setText("p" + i + "b" + j + "-" + "x".repeat(180)); // 2 lines each
                    rankedBullets.add(b);
                    all.add(b);
                }
            }
            List<LlmClient.RankedBullet> ranked = IntStream.range(0, rankedBullets.size())
                    .mapToObj(i -> TestFixtures.ranked(rankedBullets.get(i).getId(), i + 1))
                    .toList();
            Map<UUID, Project> projById = projects.stream()
                    .collect(Collectors.toMap(Project::getId, p -> p));

            List<Bullet> out = BulletSelector.select(ranked, byId(rankedBullets), projById, all, NO_KEYWORDS);

            assertNoStubEntries(out, all);
            assertFalse(out.isEmpty(), "budget pressure must not empty the resume");
            int lines = out.stream()
                    .mapToInt(b -> com.resumepipeline.llm.BulletTextRules.estimatedLines(b.getText()))
                    .sum();
            assertTrue(lines <= BulletSelector.MAX_TOTAL_LINES,
                    "trim must bring the page back inside the line budget, got " + lines);
        }

        @Test
        void nearDuplicateIsSteppedOverSoTheProjectStillReachesTheFloor() {
            // Dedup outranks the floor, but it does not *lower* the floor: a collision at rank 2
            // is stepped over by pulling deeper into the bank, so the project still ships three
            // distinct bullets. Fewer is only correct once the admissible bank is exhausted --
            // see nearDuplicateBulletIsRejectedEvenWhenBothRankedHighest for that case.
            UUID proj = UUID.randomUUID();
            Project p = TestFixtures.project(proj, Project.Kind.PROJECT, "P");
            Bullet keep = TestFixtures.bullet(UUID.randomUUID(), proj, new String[0]);
            keep.setText("Built a data pipeline processing 10000 records daily using Kafka and "
                    + "Spark for real time analytics.");
            Bullet dupe = TestFixtures.bullet(UUID.randomUUID(), proj, new String[0]);
            dupe.setText("Built a data pipeline processing 10000 records daily using Kafka and "
                    + "Spark for real time reporting.");
            Bullet clean1 = TestFixtures.bullet(UUID.randomUUID(), proj, new String[0]);
            clean1.setText("Shipped a 5-role RBAC layer over Clerk JWTs covering every admin route.");
            Bullet clean2 = TestFixtures.bullet(UUID.randomUUID(), proj, new String[0]);
            clean2.setText("Migrated the billing ledger onto append-only Postgres partitions.");
            List<Bullet> all = List.of(keep, dupe, clean1, clean2);

            // dupe is ranked second — directly in the path of the fill.
            List<LlmClient.RankedBullet> ranked = List.of(
                    TestFixtures.ranked(keep.getId(), 1),
                    TestFixtures.ranked(dupe.getId(), 2),
                    TestFixtures.ranked(clean1.getId(), 3),
                    TestFixtures.ranked(clean2.getId(), 4));

            List<Bullet> out = BulletSelector.select(ranked, byId(all),
                    projectsById(p), all, NO_KEYWORDS);

            assertEquals(BulletSelector.MAX_PER_PROJECT, out.size(),
                    "the duplicate is stepped over, not surrendered to");
            assertFalse(ids(out).contains(dupe.getId()), "near-duplicate never lands");
            assertTrue(ids(out).containsAll(List.of(keep.getId(), clean1.getId(), clean2.getId())));
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

        @Test
        void minFillBankFallbackPrefersNewKeywordsOverRepeatedOnes() {
            UUID proj = UUID.randomUUID();
            Project p = TestFixtures.project(proj, Project.Kind.PROJECT, "P");
            // The ranked pick already puts java+spring on the page.
            Bullet ranked1 = TestFixtures.bullet(UUID.randomUUID(), proj, new String[]{"java", "spring"});
            // Scores 2 on the absolute measure, but both keywords are already covered — adds nothing.
            Bullet repeat = TestFixtures.bullet(UUID.randomUUID(), proj, new String[]{"java", "spring"});
            // Scores only 1, but it is the one keyword the page is still missing.
            Bullet fresh = TestFixtures.bullet(UUID.randomUUID(), proj, new String[]{"kafka"});
            List<Bullet> all = List.of(ranked1, repeat, fresh);

            List<LlmClient.RankedBullet> ranked = List.of(TestFixtures.ranked(ranked1.getId(), 1));
            Set<String> kw = Set.of("java", "spring", "kafka");

            List<Bullet> out = BulletSelector.select(ranked, byId(List.of(ranked1)),
                    projectsById(p), all, kw);

            // Absolute tag score alone would seat `repeat` (2) ahead of `fresh` (1).
            assertEquals(List.of(ranked1.getId(), fresh.getId(), repeat.getId()), ids(out),
                    "marginal keyword gain outranks absolute tag score in the bank fallback");
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
