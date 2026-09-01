package com.resumepipeline.api.dto;

import com.resumepipeline.application.Application;
import com.resumepipeline.application.OutcomeHistory;
import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ApplicationDtos {

    public record SubmitResponse(UUID jobId) {}

    public record JobProgressResponse(List<String> lines, String status, UUID appId, String error) {}

    public record CreateApplicationRequest(
            String jdText,
            String jdUrl,
            @NotBlank String roleEmphasis,
            boolean includeCoverLetter
    ) {}

    public record UpdateOutcomeRequest(@NotBlank String outcome) {}

    public record RerenderRequest(List<UUID> selectedBulletIds) {}

    public record OutcomeHistoryEntry(UUID applicationId, String outcome, Instant changedAt) {
        public static OutcomeHistoryEntry from(OutcomeHistory h) {
            return new OutcomeHistoryEntry(h.getApplicationId(), h.getOutcome(), h.getChangedAt());
        }
    }

    /** One recruiter verdict on one rendered bullet. verdict: keep | weak | drop. */
    public record BulletVerdictDto(String bulletId, String verdict, String reason) {}

    public record ApplicationSummary(
            UUID id, String company, String role, String outcome, Instant createdAt, Integer fitScore,
            Integer recruiterScore
    ) {
        public static ApplicationSummary from(Application a) {
            return new ApplicationSummary(a.getId(), a.getCompany(), a.getRole(),
                    a.getOutcome(), a.getCreatedAt(), a.getFitScore(), a.getRecruiterScore());
        }
    }

    public record ApplicationResponse(
            UUID id, String company, String role, String jdText, String jdUrl, String roleEmphasis,
            String bulletRanking, List<UUID> selectedBulletIds,
            String coverLetter, List<String> coverLetterFlags,
            List<String> atsMatched, List<String> atsMissing,
            List<String> selectedCourses, Map<String, List<String>> selectedSkills,
            Integer fitScore, String fitVerdict, Map<String, Integer> fitDimensions,
            List<String> fitStrengths, List<String> fitGaps,
            Integer recruiterScore, String recruiterVerdict, Map<String, Integer> recruiterDimensions,
            List<BulletVerdictDto> recruiterBulletVerdicts, List<String> recruiterWeaknesses,
            String recruiterThinnestRequirement, UUID recruiterWeakestBulletId,
            boolean recruiterStale, Integer pageCount,
            boolean pdfAvailable, String pdfBase64, String tectonicLog, String outcome, Instant createdAt
    ) {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        public static ApplicationResponse from(Application a) {
            return from(a, false);
        }

        public static ApplicationResponse from(Application a, boolean includePdf) {
            String b64 = null;
            if (includePdf && a.getPdfBlob() != null && a.getPdfBlob().length > 0) {
                b64 = Base64.getEncoder().encodeToString(a.getPdfBlob());
            }
            Map<String, List<String>> skillsMap = parseSkills(a.getSelectedSkills());
            return new ApplicationResponse(
                    a.getId(), a.getCompany(), a.getRole(), a.getJdText(), a.getJdUrl(),
                    a.getRoleEmphasis(), a.getBulletRanking(),
                    Arrays.asList(a.getSelectedBulletIds()),
                    a.getCoverLetter(), Arrays.asList(a.getCoverLetterFlags()),
                    Arrays.asList(a.getAtsMatched()), Arrays.asList(a.getAtsMissing()),
                    Arrays.asList(a.getSelectedCourses()), skillsMap,
                    a.getFitScore(), a.getFitVerdict(), parseDimensions(a.getFitDimensions()),
                    Arrays.asList(a.getFitStrengths()), Arrays.asList(a.getFitGaps()),
                    a.getRecruiterScore(), a.getRecruiterVerdict(),
                    parseDimensions(a.getRecruiterDimensions()),
                    parseBulletVerdicts(a.getRecruiterBulletVerdicts()),
                    Arrays.asList(a.getRecruiterWeaknesses()),
                    a.getRecruiterThinnestRequirement(), a.getRecruiterWeakestBulletId(),
                    a.isRecruiterStale(), a.getPageCount(),
                    a.getPdfBlob() != null && a.getPdfBlob().length > 0,
                    b64, a.getTectonicLog(), a.getOutcome(), a.getCreatedAt());
        }

        private static List<BulletVerdictDto> parseBulletVerdicts(String json) {
            if (json == null || json.isBlank() || json.equals("[]")) return List.of();
            try {
                return MAPPER.readValue(json, new TypeReference<>() {});
            } catch (Exception e) {
                return List.of();
            }
        }

        private static Map<String, Integer> parseDimensions(String json) {
            if (json == null || json.isBlank() || json.equals("{}")) return Map.of();
            try {
                return MAPPER.readValue(json, new TypeReference<>() {});
            } catch (Exception e) {
                return Map.of();
            }
        }

        private static Map<String, List<String>> parseSkills(String json) {
            if (json == null || json.isBlank() || json.equals("{}")) return Map.of();
            try {
                return MAPPER.readValue(json, new TypeReference<>() {});
            } catch (Exception e) {
                return Map.of();
            }
        }
    }
}
