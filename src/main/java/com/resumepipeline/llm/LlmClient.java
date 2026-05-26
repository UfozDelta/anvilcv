package com.resumepipeline.llm;

import com.resumepipeline.progress.ProgressLog;

import java.util.List;

/**
 * LLM abstraction. Three methods, one per pipeline LLM call.
 * Each method accepts a ProgressLog so callers can stream real-time events to
 * the browser via SSE. Pass ProgressLog.noOp() when streaming is not needed.
 */
public interface LlmClient {

    BulletGenerationResult generateBullets(GenerateBulletsRequest req, ProgressLog progress);

    JdCleanResult cleanJd(String rawJd, ProgressLog progress);

    RankResult rankBullets(RankRequest req, ProgressLog progress);

    String coverLetter(CoverLetterRequest req, ProgressLog progress);

    // --- types ---

    enum SourceKind { PROJECT, EXPERIENCE }
    record GenerateBulletsRequest(
            SourceKind kind,
            String category,   // slug from CategoryLenses or "general"
            String projectName,
            String description,
            String repoContext,
            String title, String company, String location, String dates
    ) {}
    record BulletGenerationResult(List<GeneratedBullet> bullets) {}
    record GeneratedBullet(String text, List<String> tags) {}

    record JdCleanResult(String cleanJd, String company, String role, List<String> keywords) {}

    record RankRequest(String cleanJd, String company, String role, List<String> keywords, String roleEmphasis, List<BulletForMatch> bullets) {}
    record CoverLetterRequest(String cleanJd, String company, String role, String roleEmphasis, List<String> topBulletTexts) {}
    record BulletForMatch(String bulletId, String text, List<String> tags, String projectName) {}
    record RankResult(List<RankedBullet> rankedBullets, List<String> atsMatched, List<String> atsMissing) {}
    record RankedBullet(String bulletId, int rank, String why) {}
}
