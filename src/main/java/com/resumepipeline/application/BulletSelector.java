package com.resumepipeline.application;

import com.resumepipeline.bullet.Bullet;
import com.resumepipeline.llm.BulletTextRules;
import com.resumepipeline.llm.KeywordScorer;
import com.resumepipeline.llm.LlmClient;
import com.resumepipeline.project.Project;

import java.util.*;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

/**
 * Pure bullet-selection logic extracted from {@link ApplicationService#create}.
 *
 * <p>Given the LLM ranking plus the candidate/project/bank maps, decides which
 * bullets land on the resume. Four passes, in order:
 * <ol>
 *   <li><b>greedy</b> — take ranked bullets best-first, capping total and per-project;</li>
 *   <li><b>kind-floor</b> — force minimum EXPERIENCE/PROJECT diversity;</li>
 *   <li><b>min-fill</b> — pad thin projects up to the per-project cap from
 *       remaining ranked candidates, then from the raw bank by tag score;</li>
 *   <li><b>floor</b> — top every surviving project up to {@link #MAX_PER_PROJECT} ignoring
 *       every budget, then trim whole projects until the page fits again.</li>
 * </ol>
 *
 * <p>Every pass that adds a bullet first rejects it if {@link BulletTextRules#isNearDuplicate}
 * says it restates a bullet already on the resume (global across projects — see the pass 1
 * comment below for why). This is the guard that stops two AI-generated bullets making the
 * same numeric claim in different words from both landing on the same PDF.
 *
 * <p><b>Invariant.</b> Every project rendered on the resume carries exactly
 * {@link #MAX_PER_PROJECT} bullets, drawn from its <i>dedup-admissible</i> bank — the bank
 * minus anything {@code isNearDuplicate} rejects against what is already selected. Fewer only
 * when that admissible bank is exhausted. A project that cannot reach the floor within the
 * page budget is dropped <b>whole</b>; it is never rendered as a one-bullet stub, which reads
 * as padding.
 *
 * <p>Two consequences worth stating plainly, because both have been got wrong here before:
 * <ul>
 *   <li><b>Dedup outranks the floor.</b> A repeated claim is worse on a resume than a short
 *       entry, so a collision is never resolved by admitting the duplicate. It is resolved by
 *       pulling <i>deeper</i> into the bank — a duplicate at rank 2 costs nothing if there is
 *       a clean bullet at rank 4 — and only by accepting fewer once the bank runs dry.</li>
 *   <li><b>The floor is enforced by pass 4, not by passes 1-3.</b> Passes 1-3 are ordering
 *       heuristics that may under-fill; pass 4 is the guarantee. An invariant that merely
 *       emerges from a fitting algorithm regresses the next time someone edits the fitting
 *       algorithm, so it gets its own terminal pass that no budget can short-circuit.</li>
 * </ul>
 *
 * <p>No I/O, no Spring, deterministic. Callers do their own progress logging by
 * inspecting the returned list — this class emits nothing.
 */
public final class BulletSelector {

    private BulletSelector() {}

    /**
     * Hard ceiling on entries (projects) the resume may open. Previously absent, which was
     * the structural hole behind one-bullet entries: greedy capped bullets per project and in
     * total, but nothing stopped a wide ranked list from opening more projects than the page
     * could ever fill three bullets each for. The tail then stayed at one bullet apiece.
     */
    static final int MAX_ENTRIES = 6;

    /** Upper bound on bullets: {@link #MAX_ENTRIES} entries at {@link #MAX_PER_PROJECT} each. */
    static final int MAX_TOTAL = MAX_ENTRIES * 3;

    /** Bullets per project — a floor <i>and</i> a ceiling. See the invariant in the class javadoc. */
    static final int MAX_PER_PROJECT = 3;

    /**
     * Usable bullet lines on one rendered page, after heading, education, section
     * titles, project headings and the skills block eat their share of the page.
     * Estimate tied to resume.tex's layout — re-check if the template's margins,
     * heading sizes or section count change materially.
     *
     * <p>{@link #MAX_TOTAL} stays as a hard upper bound on bullet count, but a bullet
     * can render as 1-4+ lines (see {@link BulletTextRules#estimatedLines}), so a
     * count alone doesn't stop the page from overflowing — this line budget does.
     *
     * <p>ponytail: a constant, where the honest model is a function of section and entry
     * count — every entry costs ~1 line of heading <i>and</i> demands 3 more bullet lines,
     * and a constant sees neither. Derived from resume.tex's geometry (textheight 10.2in less
     * the header, skills block, section rules and entry headings, over a 12pt bullet
     * baseline), the projects-only layout that shipped has room for ~29 lines and the
     * experience-bearing layout for ~23. 29 is the former; it is deliberately optimistic,
     * because pass 4's trim drops a whole entry when it overruns and that is the outcome we
     * want anyway. Promote this to the real formula once one compiled PDF has calibrated the
     * intercept — nothing here has yet been measured against a real render.
     */
    static final int MAX_TOTAL_LINES = 29;

    private static final int MIN_EXPERIENCE_PROJECTS = 2;
    private static final int MIN_PROJECT_ENTRIES = 3;

    /** Keyword-in-text score for a bullet against the (lower-cased) JD keyword set. */
    static ToLongFunction<Bullet> tagScore(Set<String> keywordsLower) {
        return KeywordScorer.score(keywordsLower);
    }

    /**
     * Run all four selection passes and return the chosen bullets in selection order.
     *
     * @param rankedSorted LLM-ranked bullets, already sorted best-first
     * @param bulletById   candidate bullets keyed by id (the LLM-ranked subset)
     * @param projectById  all of the user's projects keyed by id
     * @param allBullets   the user's entire bullet bank (for the min-fill fallback)
     * @param keywordsLower lower-cased JD keywords driving tag-score fallback ordering
     */
    public static List<Bullet> select(List<LlmClient.RankedBullet> rankedSorted,
                                      Map<UUID, Bullet> bulletById,
                                      Map<UUID, Project> projectById,
                                      List<Bullet> allBullets,
                                      Set<String> keywordsLower) {
        ToLongFunction<Bullet> tagScore = tagScore(keywordsLower);

        // Pass 1: greedy top-N, capped per project and by rendered-line budget.
        //
        // selectedTexts mirrors `selected` (a list, not a set: two selected bullets could in
        // principle share text pre-dedup, and eviction below removes by value) and is checked
        // before every add across all four passes, so two bullets describing the same claim
        // never both land on the resume — see the class javadoc for the shipped-PDF case this
        // covers. Chosen as a global check, not per-project: the evidence pair happened to share
        // a project, but the same over-claim duplicated across two *different* projects (e.g. a
        // "Project" entry and an "Experience" entry describing the same backtester) is equally
        // bad on a resume, and nothing about the near-duplicate signal (word-set overlap) is
        // project-scoped.
        LinkedHashMap<UUID, Integer> perProject = new LinkedHashMap<>();
        List<Bullet> selected = new ArrayList<>();
        List<String> selectedTexts = new ArrayList<>();
        int lines = 0;
        for (LlmClient.RankedBullet rb : rankedSorted) {
            if (selected.size() >= MAX_TOTAL) break;
            UUID bid = parseUuid(rb.bulletId());
            if (bid == null) continue;
            Bullet b = bulletById.get(bid);
            if (b == null) continue;
            int count = perProject.getOrDefault(b.getProjectId(), 0);
            if (count >= MAX_PER_PROJECT) continue;
            // Entry cap. Opening entry MAX_ENTRIES+1 here is how one-bullet entries were born:
            // greedy would seat a project on the strength of a single ranked bullet, spend the
            // rest of the page on higher-ranked projects, and leave min-fill nothing to pad it
            // with. Refuse to open the entry at all — better four full entries than six stubs.
            if (count == 0 && perProject.size() >= MAX_ENTRIES) continue;
            if (BulletTextRules.isNearDuplicate(b.getText(), selectedTexts)) continue;
            int bLines = BulletTextRules.estimatedLines(b.getText());
            // A later, shorter bullet may still fit even if this one doesn't — skip, don't stop.
            if (lines + bLines > MAX_TOTAL_LINES) continue;
            perProject.put(b.getProjectId(), count + 1);
            selected.add(b);
            selectedTexts.add(b.getText());
            lines += bLines;
        }

        // Pass 2: kind-floor — force EXPERIENCE/PROJECT diversity if greedy missed it.
        Set<UUID> selectedIds = selected.stream().map(Bullet::getId)
                .collect(Collectors.toCollection(HashSet::new));
        long expDistinct = distinctProjectsOfKind(selected, projectById, Project.Kind.EXPERIENCE);
        long projDistinct = distinctProjectsOfKind(selected, projectById, Project.Kind.PROJECT);

        if (expDistinct < MIN_EXPERIENCE_PROJECTS || projDistinct < MIN_PROJECT_ENTRIES) {
            Set<UUID> selectedProjects = selected.stream().map(Bullet::getProjectId)
                    .collect(Collectors.toCollection(HashSet::new));
            for (LlmClient.RankedBullet rb : rankedSorted) {
                if (expDistinct >= MIN_EXPERIENCE_PROJECTS && projDistinct >= MIN_PROJECT_ENTRIES) break;
                UUID bid = parseUuid(rb.bulletId());
                if (bid == null || selectedIds.contains(bid)) continue;
                Bullet b = bulletById.get(bid);
                if (b == null) continue;
                Project p = projectById.get(b.getProjectId());
                if (p == null || selectedProjects.contains(b.getProjectId())) continue;
                boolean wanted = (p.getKind() == Project.Kind.EXPERIENCE && expDistinct < MIN_EXPERIENCE_PROJECTS)
                        || (p.getKind() == Project.Kind.PROJECT && projDistinct < MIN_PROJECT_ENTRIES);
                if (!wanted) continue;
                if (BulletTextRules.isNearDuplicate(b.getText(), selectedTexts)) continue;
                int bLines = BulletTextRules.estimatedLines(b.getText());
                // At the cap, this pass used to add anyway — up to 5 bullets past MAX_TOTAL, which
                // is a two-page PDF. Breaking instead would silently abandon the diversity floor
                // this pass exists to guarantee, so make room rather than choosing between them:
                // drop the weakest bullet of whichever project is most over-represented. The same
                // applies to the line budget: evict to make room, and if that still doesn't fit
                // (the evicted bullet was shorter than the one we're trying to add), move on to
                // the next candidate rather than giving up on the whole pass.
                if (selected.size() >= MAX_TOTAL || lines + bLines > MAX_TOTAL_LINES) {
                    Bullet evicted = evictWeakestFromFullestProject(selected);
                    if (evicted == null) break;
                    lines -= BulletTextRules.estimatedLines(evicted.getText());
                    // The evicted bullet no longer renders, so it must stop blocking later
                    // near-duplicate candidates too — text list mirrors `selected` exactly.
                    selectedTexts.remove(evicted.getText());
                    if (selected.size() >= MAX_TOTAL || lines + bLines > MAX_TOTAL_LINES) continue;
                }
                selected.add(b); selectedIds.add(bid); selectedProjects.add(b.getProjectId());
                selectedTexts.add(b.getText());
                lines += bLines;
                if (p.getKind() == Project.Kind.EXPERIENCE) expDistinct++; else projDistinct++;
            }
        }

        // Pass 3: min-fill — pad each on-resume project up to the per-project cap.
        Map<UUID, List<Bullet>> allByProject = allBullets.stream()
                .collect(Collectors.groupingBy(Bullet::getProjectId));
        // Rank order, not hash order. This was a HashSet, so min-fill padded projects in
        // UUID-hash order — arbitrary with respect to quality, which meant a tight budget could
        // starve the *top-ranked* entry while padding a weaker one to three. `selected` is
        // already best-first, so distinct() over it is the order the page should be spent in.
        for (UUID pid : selected.stream().map(Bullet::getProjectId).distinct().toList()) {
            if (selected.size() >= MAX_TOTAL) break;
            int have = (int) selected.stream().filter(b -> b.getProjectId().equals(pid)).count();
            if (have >= MAX_PER_PROJECT) continue;

            // Source 1: remaining LLM-ranked candidates for this project (respect LLM signal).
            for (LlmClient.RankedBullet rb : rankedSorted) {
                if (have >= MAX_PER_PROJECT || selected.size() >= MAX_TOTAL) break;
                UUID bid = parseUuid(rb.bulletId());
                if (bid == null || selectedIds.contains(bid)) continue;
                Bullet b = bulletById.get(bid);
                if (b == null || !b.getProjectId().equals(pid)) continue;
                if (BulletTextRules.isNearDuplicate(b.getText(), selectedTexts)) continue;
                int bLines = BulletTextRules.estimatedLines(b.getText());
                if (lines + bLines > MAX_TOTAL_LINES) continue;
                selected.add(b); selectedIds.add(bid); selectedTexts.add(b.getText()); have++;
                lines += bLines;
            }

            // Source 2: raw bank fallback for thin banks, sorted by tag score.
            if (have < MAX_PER_PROJECT && selected.size() < MAX_TOTAL) {
                List<Bullet> bank = allByProject.getOrDefault(pid, List.of()).stream()
                        .filter(b -> !selectedIds.contains(b.getId()))
                        .sorted(Comparator.comparingLong(tagScore).reversed())
                        .toList();
                for (Bullet b : bank) {
                    if (have >= MAX_PER_PROJECT || selected.size() >= MAX_TOTAL) break;
                    if (BulletTextRules.isNearDuplicate(b.getText(), selectedTexts)) continue;
                    int bLines = BulletTextRules.estimatedLines(b.getText());
                    if (lines + bLines > MAX_TOTAL_LINES) continue;
                    selected.add(b); selectedIds.add(b.getId()); selectedTexts.add(b.getText()); have++;
                    lines += bLines;
                }
            }
        }

        // ── Pass 4: the floor ───────────────────────────────────────────────────────────
        // Everything above is best-effort. Pass 1 stops on a budget mid-project, pass 3 gives
        // up silently when the page is nearly spent, and pass 2's eviction actively *creates*
        // short entries: it takes a project from two bullets down to one, then seats the freed
        // slot on a brand-new project that opens at exactly one. Two stubs per cycle, which is
        // precisely the shipped-PDF symptom.
        //
        // So the floor is enforced here instead, unconditionally: no line budget and no count
        // cap may starve a project in this pass, because a starved project is the thing being
        // fixed. Passes 1-3 are thereby demoted to *ordering* heuristics whose under-fills are
        // now recoverable, and the page budget is re-imposed afterwards at project granularity.
        //
        // Eligibility is "not currently in `selected`", deliberately NOT `selectedIds`: that
        // set retains evicted ids on purpose (see evictWeakestFromFullestProject), which is
        // exactly what stopped pass 3 from refilling a project pass 2 had raided.
        Set<UUID> liveIds = selected.stream().map(Bullet::getId)
                .collect(Collectors.toCollection(HashSet::new));
        for (UUID pid : selected.stream().map(Bullet::getProjectId).distinct().toList()) {
            int have = (int) selected.stream().filter(b -> b.getProjectId().equals(pid)).count();
            if (have >= MAX_PER_PROJECT) continue;

            // Source 1: this project's remaining ranked candidates, best-first.
            for (LlmClient.RankedBullet rb : rankedSorted) {
                if (have >= MAX_PER_PROJECT) break;
                UUID bid = parseUuid(rb.bulletId());
                if (bid == null || liveIds.contains(bid)) continue;
                Bullet b = bulletById.get(bid);
                if (b == null || !b.getProjectId().equals(pid)) continue;
                if (BulletTextRules.isNearDuplicate(b.getText(), selectedTexts)) continue;
                selected.add(b); liveIds.add(bid); selectedTexts.add(b.getText()); have++;
            }

            // Source 2: the raw bank by tag score. This is the only source that reaches an
            // *unranked* bullet, and therefore the one that supplies the depth to step past a
            // near-duplicate instead of surrendering at the first collision — the caller's
            // pre-filter admits at most 4 ranked candidates per project, so source 1 alone
            // holds a single spare. Both loops stop at MAX_PER_PROJECT *or* exhaustion, which
            // is what makes the result min(MAX_PER_PROJECT, admissible bank) by construction.
            if (have < MAX_PER_PROJECT) {
                List<Bullet> bank = allByProject.getOrDefault(pid, List.of()).stream()
                        .filter(b -> !liveIds.contains(b.getId()))
                        .sorted(Comparator.comparingLong(tagScore).reversed())
                        .toList();
                for (Bullet b : bank) {
                    if (have >= MAX_PER_PROJECT) break;
                    if (BulletTextRules.isNearDuplicate(b.getText(), selectedTexts)) continue;
                    selected.add(b); liveIds.add(b.getId()); selectedTexts.add(b.getText()); have++;
                }
            }
        }

        // Trim. The top-up above ignores the page budget, so re-impose it here — at PROJECT
        // granularity, the only granularity that preserves the floor. Dropping a whole entry is
        // also the better resume: an entry rendered with one bullet reads as padding, so if the
        // page cannot afford three bullets for it, it should not appear at all.
        //
        // Victims come off the back of rank order (weakest entry first), and an entry is spared
        // when removing it would break the kind floor pass 2 just worked to satisfy.
        while (selected.size() > MAX_TOTAL
                || totalLines(selected) > MAX_TOTAL_LINES
                || selected.stream().map(Bullet::getProjectId).distinct().count() > MAX_ENTRIES) {
            List<UUID> order = selected.stream().map(Bullet::getProjectId).distinct().toList();
            UUID victim = null;
            for (int i = order.size() - 1; i >= 0 && victim == null; i--) {
                UUID pid = order.get(i);
                Project p = projectById.get(pid);
                if (p == null) { victim = pid; break; } // unknown kind holds no floor up
                long floor = p.getKind() == Project.Kind.EXPERIENCE
                        ? MIN_EXPERIENCE_PROJECTS : MIN_PROJECT_ENTRIES;
                if (distinctProjectsOfKind(selected, projectById, p.getKind()) > floor) victim = pid;
            }
            // Every remaining entry is holding up a kind floor: overrun the page rather than
            // silently abandon the diversity guarantee. Unreachable while the floors sum below
            // MAX_ENTRIES, but the loop must not spin if that ever stops being true.
            if (victim == null) break;
            UUID v = victim;
            List<Bullet> doomed = selected.stream().filter(b -> b.getProjectId().equals(v)).toList();
            selected.removeIf(b -> b.getProjectId().equals(v));
            doomed.forEach(b -> selectedTexts.remove(b.getText()));
        }

        return selected;
    }

    /** Total estimated rendered lines for a selection — the page budget's unit. */
    private static int totalLines(List<Bullet> bullets) {
        return bullets.stream().mapToInt(b -> BulletTextRules.estimatedLines(b.getText())).sum();
    }

    static final int MIN_SKILLS_PER_CATEGORY = 6;
    static final String[] SKILL_KEYS = {"languages", "frameworks", "databases", "devops"};

    /**
     * Skill-floor pass: each category must carry at least {@link #MIN_SKILLS_PER_CATEGORY}
     * items. The LLM's selected skills come first; the remainder is padded from the raw
     * profile skills (in their original order), de-duplicated.
     *
     * @param selectedSkills LLM-chosen skills per category (may be empty/missing keys)
     * @param rawSkills      full profile skills per category, used to pad up to the floor
     * @return per-category skills padded to the floor, preserving insertion order
     */
    public static Map<String, List<String>> fillSkills(Map<String, List<String>> selectedSkills,
                                                       Map<String, List<String>> rawSkills) {
        Map<String, List<String>> filled = new LinkedHashMap<>(
                selectedSkills == null ? Map.of() : selectedSkills);
        for (String key : SKILL_KEYS) {
            List<String> sel = new ArrayList<>(filled.getOrDefault(key, List.of()));
            Set<String> seen = new LinkedHashSet<>(sel);
            for (String item : rawSkills.getOrDefault(key, List.of())) {
                if (sel.size() >= MIN_SKILLS_PER_CATEGORY) break;
                if (seen.add(item)) sel.add(item);
            }
            filled.put(key, sel);
        }
        return filled;
    }

    /**
     * Remove the lowest-ranked bullet of the project holding the most slots, freeing one for a
     * diversity pick. {@code selected} is in rank order, so the last entry for a project is its
     * weakest. Projects down to their final bullet are never raided — evicting those would undo
     * the very diversity this is making room for.
     *
     * <p>The evicted bullet's id deliberately stays in the caller's {@code selectedIds}: it has
     * been considered and passed over, and letting pass 3 pick it straight back up would just
     * churn the selection without changing its size.
     *
     * <p>ponytail: this still takes a project from two bullets to one, and the bullet it makes
     * room for opens a new project at exactly one — historically a direct source of stub
     * entries. Left as is because pass 4 tops both back up (it matches on live selection, not
     * on {@code selectedIds}), so the damage is now self-healing, and a guard here that refused
     * to raid a project at the floor could leave the kind floor unsatisfiable. Revisit only if
     * the churn shows up in a real render.
     *
     * @return the evicted bullet if a slot was freed; null when no project has a spare bullet to give up
     */
    private static Bullet evictWeakestFromFullestProject(List<Bullet> selected) {
        Map<UUID, Long> counts = selected.stream()
                .collect(Collectors.groupingBy(Bullet::getProjectId, Collectors.counting()));
        UUID fullest = counts.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        if (fullest == null) return null;
        for (int i = selected.size() - 1; i >= 0; i--) {
            if (selected.get(i).getProjectId().equals(fullest)) {
                return selected.remove(i);
            }
        }
        return null;
    }

    private static long distinctProjectsOfKind(List<Bullet> selected, Map<UUID, Project> projectById,
                                               Project.Kind kind) {
        return selected.stream().map(Bullet::getProjectId).distinct()
                .filter(pid -> { Project p = projectById.get(pid); return p != null && p.getKind() == kind; })
                .count();
    }

    private static UUID parseUuid(String s) {
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }
}
