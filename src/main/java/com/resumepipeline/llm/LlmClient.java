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

    /**
     * Score how well the job description fits the candidate, judged against their profile
     * skills and their project/experience history — NOT against the bullet-bank slice
     * {@link #rankBullets} sees, which is already filtered to this JD and would score every
     * application highly by construction. Writes no resume content: the result is advisory
     * only and never feeds the rendered PDF.
     */
    FitResult scoreFit(FitRequest req, ProgressLog progress, TokenAccumulator tokens);

    /**
     * Grade the RENDERED page against the job description — a different question from
     * {@link #scoreFit}, which grades the candidate. This call deliberately sees only the
     * bullets that were selected onto the PDF: never the bullet bank, never the ranking,
     * never the rank order. It judges what a recruiter actually holds in their hand.
     * Writes no resume content: the result is advisory only and never feeds the rendered PDF.
     */
    RecruiterResult reviewResume(RecruiterRequest req, ProgressLog progress, TokenAccumulator tokens);

    /**
     * One turn of the server-side repo explorer's tool loop: given its instructions and the
     * transcript of tool calls and observations so far, choose the next tool call or finish
     * with the extracted context. The explorer, not the model, executes the tools.
     */
    ExploreStep exploreStep(ExploreStepRequest req, ProgressLog progress, TokenAccumulator tokens);

    /** Repo map, level 1: what one module does and why, from its skeleton and its central file. */
    ModuleSummary summarizeModule(ModuleSummaryRequest req, ProgressLog progress, TokenAccumulator tokens);

    /**
     * Repo map, level 2: the whole project from its module summaries, README, and counted facts —
     * what it does, for whom, its subsystems (each tagged with the lenses it feeds), and its main flows.
     */
    ProjectSummaryResult summarizeProject(ProjectSummaryRequest req, ProgressLog progress, TokenAccumulator tokens);

    // --- types ---

    enum SourceKind { PROJECT, EXPERIENCE }
    record GenerateBulletsRequest(
            java.util.UUID userId,
            SourceKind kind,
            String category,   // slug from CategoryLenses or "general"
            String projectName,
            String description,
            /**
             * Long-form architecture/role overview (the project's "Info & Context" field) —
             * distinct from {@code description}. Collected from users and from the context
             * extractor's output but, until now, never actually reached generation.
             */
            String contextDescription,
            String repoContext,
            String techStack,
            String yourRole,
            String ownership,
            String scaleImpact,
            String hardestProblem,
            String technicalDecisions,
            String userImpact,
            String securityPosture,
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

    record ProjectSummary(String name, String kind, String role, String dates, String description) {}
    record FitRequest(String cleanJd, String company, String role, List<String> keywords, String roleEmphasis,
                      List<SkillCategory> skillCategories, List<ProjectSummary> projects) {}
    record FitResult(int technical, int experience, int overall, String verdict,
                     List<String> strengths, List<String> gaps) {}

    /** A bullet as it appears on the compiled PDF. */
    record RenderedBullet(String bulletId, String text, String projectName) {}
    /** verdict: keep | weak | drop */
    record BulletVerdict(String bulletId, String verdict, String reason) {}
    record RecruiterRequest(String cleanJd, String company, String role, List<String> keywords,
                            String roleEmphasis, List<RenderedBullet> bullets,
                            java.util.Map<String, List<String>> skills, List<String> courses) {}
    record ExploreStepRequest(String instructions, String transcript) {}
    /** action: list_tree | read_file | search_code | git_log | finish. {@code result} is set only on finish. */
    record ExploreStep(String action, String path, String query, String reason, ExtractResult result) {}
    /** The 11-key context JSON (anvilcv-context-mcp's schema.json) plus the citations behind it. */
    record ExtractResult(String name, String techStack, String description, String yourRole, String ownership,
                         String scaleImpact, String hardestProblem, String technicalDecisions,
                         String userImpact, String securityPosture, List<String> category,
                         List<EvidenceRef> evidence) {}
    /** A citation: a file line range the explorer read, or a commit sha it saw in git_log. */
    record EvidenceRef(String field, String claim, String path, int startLine, int endLine, String commit) {}

    record ModuleSummaryRequest(String projectName, String modulePath, List<String> files, List<String> symbols,
                                List<String> routes, List<String> dependsOn, String code) {}
    /** summary: what it does and how. purpose: the user-facing reason it exists. */
    record ModuleSummary(String summary, String purpose) {}

    /** modules: one line each, "path — summary (purpose)", highest ranked first. */
    record ProjectSummaryRequest(String projectName, String readme, String manifests, List<String> facts,
                                 List<String> modules, List<String> lenses) {}
    record ProjectSummaryResult(String overview, String audience, List<SubsystemSummary> subsystems, List<FlowSummary> flows) {}
    record SubsystemSummary(String name, String purpose, List<String> lenses, List<String> modules) {}
    record FlowSummary(String name, List<String> steps) {}

    record RecruiterResult(int evidenceStrength, int relevanceDensity, int overall, String verdict,
                           String weakestBulletId, String thinnestRequirement,
                           List<String> weaknesses, List<BulletVerdict> bulletVerdicts) {}
}
