package com.resumepipeline.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request/response shapes for the external-agent bridge. */
public final class AgentDtos {

    private AgentDtos() {}

    public record CreateStoryRequest(
            @NotNull UUID projectId,
            @NotBlank @Size(max = 500) String title,
            @Size(max = 40) List<@Size(max = 4000) String> evidence,
            @Size(max = 4) List<String> lenses) {}

    public record StoryResponse(UUID storyId) {}

    public record AgentBullet(
            @NotBlank @Size(max = 4000) String text,
            @Size(max = 50) List<String> tags,
            String lens,
            String angle,
            @Size(max = 2000) String judgeNote,
            String status) {}

    public record CreateBulletsRequest(@NotEmpty @Size(max = 12) List<AgentBullet> bullets) {}

    public record BulletsResponse(List<UUID> bulletIds) {}

    public record CheckBulletsRequest(
            @NotEmpty @Size(max = 100) List<@NotBlank String> texts,
            @Size(max = 200) List<String> existingTexts,
            UUID projectId,
            @Size(max = 30000) String sourceContext) {}

    public record BulletCheck(
            String text,
            String decision,
            int charCount,
            int estimatedLines,
            String vanityCount,
            List<String> fabricatedNumbers,
            boolean forbiddenOpener,
            boolean padded,
            boolean nearDuplicate,
            String cappedText,
            boolean autoSelectable) {}

    public record BulletChecksResponse(
            List<BulletCheck> checks,
            int singleLow, int singleHigh,
            int deadLow, int deadHigh,
            int doubleLow, int doubleHigh) {}

    public record CheckCoverageRequest(
            @NotBlank @Size(max = 30000) String jdText,
            @Size(max = 40000) String renderedText,
            @Size(max = 200) List<String> keywords) {}

    public record CoverageItem(String text, String kind, String status) {}

    public record CoverageResponse(
            List<CoverageItem> items,
            List<String> coverageGaps,
            List<String> hardGateMisses) {}

    public record CreateAgentApplicationRequest(
            @Size(max = 30000) String jdText,
            @Size(max = 2048) String jdUrl,
            @Size(max = 100) String roleEmphasis,
            @Size(max = 200) String company,
            @Size(max = 200) String role,
            @Size(max = 200) List<String> keywords,
            @NotEmpty @Size(max = 200) List<UUID> selectedBulletIds,
            Map<String, List<String>> selectedSkills,
            @Size(max = 50) List<String> selectedCourses,
            @Size(max = 20000) String coverLetter,
            List<RankEntry> ranking,
            Integer fitScore,
            @Size(max = 200) String fitVerdict,
            @Size(max = 20) List<String> fitStrengths,
            @Size(max = 20) List<String> fitGaps) {}

    /** A ranking row in the shape the application UI expects. */
    public record RankEntry(UUID bulletId, int rank, @Size(max = 500) String why) {}

    /** Patch scoring/ranking on an agent-created application after the agent has assessed it. */
    public record UpdateAgentApplicationRequest(
            List<RankEntry> ranking,
            Integer fitScore,
            @Size(max = 200) String fitVerdict,
            @Size(max = 20) List<String> fitStrengths,
            @Size(max = 20) List<String> fitGaps) {}
}
