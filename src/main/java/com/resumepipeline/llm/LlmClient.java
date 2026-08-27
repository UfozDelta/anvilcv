package com.resumepipeline.llm;

import com.resumepipeline.progress.ProgressLog;

import java.util.List;

/**
 * LLM abstraction. One method per pipeline LLM call.
 * Each method accepts a ProgressLog so callers can stream real-time events to
 * the browser via SSE. Pass ProgressLog.noOp() when streaming is not needed.
 */
public interface LlmClient {

    BulletGenerationResult generateBullets(GenerateBulletsRequest req, ProgressLog progress, TokenAccumulator tokens);

    JdCleanResult cleanJd(String rawJd, ProgressLog progress, TokenAccumulator tokens);

    RankResult rankBullets(RankRequest req, ProgressLog progress, TokenAccumulator tokens);

    /**
     * Rewrite already-persisted bullets whose rendered length falls outside the user's
     * configured bands, so they stop pushing the rendered resume onto a second page.
     * Unlike {@link #generateBullets} this writes no new content: each returned bullet is a
     * rephrasing of the one it shares an id with, and the caller re-runs the same length and
     * fabricated-metric checks before persisting anything.
     */
    RefitResult refitBullets(RefitRequest req, ProgressLog progress, TokenAccumulator tokens);

    String coverLetter(CoverLetterRequest req, ProgressLog progress, TokenAccumulator tokens);

    // --- types ---

    enum SourceKind { PROJECT, EXPERIENCE }
    record GenerateBulletsRequest(
            java.util.UUID userId,
            SourceKind kind,
            String category,   // slug from CategoryLenses or "general"
            String projectName,
            String description,
            String repoContext,
            String techStack,
            String yourRole,
            String ownership,
            String scaleImpact,
            String hardestProblem,
            String title, String company, String location, String dates,
            /** Bullets already in the bank for this project — the prompt is told to avoid repeating them. */
            List<String> existingBullets,
            /**
             * The OTHER category slugs generating concurrently in this same batch — not the bank.
             * Their bullets do not exist yet, so the prompt can only be told to stay off their
             * angles; empty for a standalone generation with no siblings.
             */
            List<String> siblingCategories
    ) {}
    record BulletGenerationResult(List<GeneratedBullet> bullets) {}
    record GeneratedBullet(String text, List<String> tags) {}

    /**
     * A batch of over/under-length bullets to rewrite. Ids are opaque to the LLM layer and
     * exist only so the caller can map replies back to rows — the reply order is not trusted.
     */
    record RefitRequest(java.util.UUID userId, List<BulletToRefit> bullets) {}
    record BulletToRefit(String id, String text) {}
    record RefitResult(List<BulletToRefit> bullets) {}

    record JdCleanResult(String cleanJd, String company, String role, List<String> keywords) {}

    record RankRequest(String cleanJd, String company, String role, List<String> keywords, String roleEmphasis, List<BulletForMatch> bullets, List<String> courses, List<SkillCategory> skillCategories) {}
    record CoverLetterRequest(String cleanJd, String company, String role, String roleEmphasis, List<String> topBulletTexts) {}
    record BulletForMatch(String bulletId, String text, List<String> tags, String projectName) {}
    record SkillCategory(String name, List<String> items) {}
    record RankResult(List<RankedBullet> rankedBullets, List<String> atsMatched, List<String> atsMissing, List<String> selectedCourses, java.util.Map<String, List<String>> selectedSkills) {}
    record RankedBullet(String bulletId, int rank, String why) {}
}
