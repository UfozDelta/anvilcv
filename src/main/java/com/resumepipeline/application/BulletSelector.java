package com.resumepipeline.application;

import com.resumepipeline.bullet.Bullet;
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
 *       remaining ranked candidates, then from the raw bank by tag score.</li>
 * </ol>
 *
 * <p>No I/O, no Spring, deterministic. Callers do their own progress logging by
 * inspecting the returned list — this class emits nothing.
 */
public final class BulletSelector {

    private BulletSelector() {}

    static final int MAX_TOTAL = 15;
    static final int MAX_PER_PROJECT = 3; // also the per-project minimum-fill target

    /** Soft page-budget warning threshold — above this a one-page PDF is at risk. Must stay &lt;= MAX_TOTAL. */
    static final int PAGE_WARN_THRESHOLD = 12;

    private static final int MIN_EXPERIENCE_PROJECTS = 2;
    private static final int MIN_PROJECT_ENTRIES = 3;

    /** Keyword-in-text score for a bullet against the (lower-cased) JD keyword set. */
    static ToLongFunction<Bullet> tagScore(Set<String> keywordsLower) {
        return KeywordScorer.score(keywordsLower);
    }

    /**
     * Run all three selection passes and return the chosen bullets in selection order.
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

        // Pass 1: greedy top-N, capped per project.
        LinkedHashMap<UUID, Integer> perProject = new LinkedHashMap<>();
        List<Bullet> selected = new ArrayList<>();
        for (LlmClient.RankedBullet rb : rankedSorted) {
            if (selected.size() >= MAX_TOTAL) break;
            UUID bid = parseUuid(rb.bulletId());
            if (bid == null) continue;
            Bullet b = bulletById.get(bid);
            if (b == null) continue;
            int count = perProject.getOrDefault(b.getProjectId(), 0);
            if (count >= MAX_PER_PROJECT) continue;
            perProject.put(b.getProjectId(), count + 1);
            selected.add(b);
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
                // At the cap, this pass used to add anyway — up to 5 bullets past MAX_TOTAL, which
                // is a two-page PDF. Breaking instead would silently abandon the diversity floor
                // this pass exists to guarantee, so make room rather than choosing between them:
                // drop the weakest bullet of whichever project is most over-represented.
                if (selected.size() >= MAX_TOTAL && !evictWeakestFromFullestProject(selected)) break;
                selected.add(b); selectedIds.add(bid); selectedProjects.add(b.getProjectId());
                if (p.getKind() == Project.Kind.EXPERIENCE) expDistinct++; else projDistinct++;
            }
        }

        // Pass 3: min-fill — pad each on-resume project up to the per-project cap.
        Map<UUID, List<Bullet>> allByProject = allBullets.stream()
                .collect(Collectors.groupingBy(Bullet::getProjectId));
        Set<UUID> selectedProjectIds = selected.stream().map(Bullet::getProjectId)
                .collect(Collectors.toCollection(HashSet::new));

        for (UUID pid : new ArrayList<>(selectedProjectIds)) {
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
                selected.add(b); selectedIds.add(bid); have++;
            }

            // Source 2: raw bank fallback for thin banks, sorted by tag score.
            if (have < MAX_PER_PROJECT && selected.size() < MAX_TOTAL) {
                List<Bullet> bank = allByProject.getOrDefault(pid, List.of()).stream()
                        .filter(b -> !selectedIds.contains(b.getId()))
                        .sorted(Comparator.comparingLong(tagScore).reversed())
                        .toList();
                for (Bullet b : bank) {
                    if (have >= MAX_PER_PROJECT || selected.size() >= MAX_TOTAL) break;
                    selected.add(b); selectedIds.add(b.getId()); have++;
                }
            }
        }

        return selected;
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
     * @return true if a slot was freed; false when no project has a spare bullet to give up
     */
    private static boolean evictWeakestFromFullestProject(List<Bullet> selected) {
        Map<UUID, Long> counts = selected.stream()
                .collect(Collectors.groupingBy(Bullet::getProjectId, Collectors.counting()));
        UUID fullest = counts.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        if (fullest == null) return false;
        for (int i = selected.size() - 1; i >= 0; i--) {
            if (selected.get(i).getProjectId().equals(fullest)) {
                selected.remove(i);
                return true;
            }
        }
        return false;
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
