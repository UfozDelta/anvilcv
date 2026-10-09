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

    /**
     * Bank pass 1 of 2: pick the project's strongest stories, each backed by verbatim evidence
     * from the source and tagged with the requested lenses it truly fits. Evidence that cannot be
     * found in the source is dropped, and so is a story left with none.
     */
    StoryResult findStories(StoryRequest req, ProgressLog progress, TokenAccumulator tokens);

    /**
     * One (story, lens) slot of the bank build: writes {@code count} wordings and drops the ones
     * the code filter rejects. Dedup and rating are the caller's. {@code alreadyWritten} are
     * wordings the story already has; the model is asked to say them differently.
     */
    SlotCandidates writeSlotCandidates(GenerateBulletsRequest source, Story story, String lens, int count,
                                       List<String> alreadyWritten, ProgressLog progress, TokenAccumulator tokens);

    /**
     * Recruiter checklist over a slot's candidates, in the order given: writes a note per candidate,
     * then a 1-5 score for each. Returns one score per candidate, or empty when the reply is
     * unreadable or does not line up with the candidates.
     */
    List<Integer> scoreCandidates(Story story, List<String> candidates, ProgressLog progress, TokenAccumulator tokens);

    JdCleanResult cleanJd(String rawJd, ProgressLog progress, TokenAccumulator tokens);

    /**
     * Like {@link #cleanJd} but the model only extracts company, role and keywords; the
     * returned cleanJd is the (capped) input text as-is. For JD text that is already just the
     * posting (JSON-LD), where re-writing it out costs output tokens and adds nothing.
     */
    JdCleanResult extractJd(String rawJd, ProgressLog progress, TokenAccumulator tokens);

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
            List<String> siblingCategories,
            /**
             * This lens's slice of the repo map — the subsystems tagged for it (or picked by the
             * user), with their modules' what/how/why. Null when the project has no map. Varies
             * per lens, so it goes at the prompt's tail with the lens, not in the shared prefix.
             */
            String lensFocus
    ) {}
    record BulletGenerationResult(List<GeneratedBullet> bullets) {}
    /** storyId and lens are set on the story bank path; null on the single-lens path. */
    record GeneratedBullet(String text, List<String> tags, String storyId, String lens) {
        public GeneratedBullet(String text, List<String> tags) { this(text, tags, null, null); }
    }

    /**
     * {@code source.category()} is unused here; {@code lenses} are the ones the user asked for;
     * {@code bank} is what the project's bank already covers, shown to findStories only;
     * {@code maxStories} is how many new stories findStories may ask for.
     */
    record StoryRequest(GenerateBulletsRequest source, List<String> lenses, BankCoverage bank, int maxStories) {
        public StoryRequest(GenerateBulletsRequest source, List<String> lenses) {
            this(source, lenses, BankCoverage.EMPTY, 8);
        }
    }
    /** A story the bank already holds, as findStories sees it: title and lenses, no evidence. */
    record KnownStory(String title, List<String> lenses) {}
    /**
     * What the bank already covers: live stories, the text of bullets written without a story,
     * and the titles of stories the user rejected every wording of.
     */
    record BankCoverage(List<KnownStory> live, List<String> coveredWork, List<String> dismissed) {
        public static final BankCoverage EMPTY = new BankCoverage(List.of(), List.of(), List.of());
    }
    /** id: the model's own key ("s1"), only meaningful within one generation run. */
    record Story(String id, String title, List<String> evidence, List<String> lenses) {}
    record StoryResult(List<Story> stories, List<String> unsupportedLenses) {}
    /**
     * A wording that passed the code filter. Tags are the ones the bullet actually mentions.
     * {@code angle} is one of outcome, decision, scale, failure, or null when the model gave none.
     */
    record Candidate(String text, List<String> tags, String angle) {
        public Candidate(String text, List<String> tags) { this(text, tags, null); }
    }
    /** {@code written} is what the model returned; {@code kept} is what survived the filter. */
    record SlotCandidates(List<Candidate> kept, int written, int filtered) {}

    /**
     * A batch of over/under-length bullets to rewrite. Ids are opaque to the LLM layer and
     * exist only so the caller can map replies back to rows — the reply order is not trusted.
     */
    record RefitRequest(java.util.UUID userId, List<BulletToRefit> bullets) {}
    record BulletToRefit(String id, String text) {}
    record RefitResult(List<BulletToRefit> bullets) {}

    /** {@code lenses}: up to 2 {@link CategoryLenses} slugs the JD leans toward, best first; empty = generalist. */
    record JdCleanResult(String cleanJd, String company, String role, List<String> keywords, List<String> lenses) {
        public JdCleanResult(String cleanJd, String company, String role, List<String> keywords) {
            this(cleanJd, company, role, keywords, List.of());
        }
    }

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
