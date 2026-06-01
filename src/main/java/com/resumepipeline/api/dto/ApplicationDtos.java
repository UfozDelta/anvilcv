package com.resumepipeline.api.dto;

import com.resumepipeline.application.Application;
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

    public record ApplicationSummary(
            UUID id, String company, String role, String outcome, Instant createdAt
    ) {
        public static ApplicationSummary from(Application a) {
            return new ApplicationSummary(a.getId(), a.getCompany(), a.getRole(),
                    a.getOutcome(), a.getCreatedAt());
        }
    }

    public record ApplicationResponse(
            UUID id, String company, String role, String jdText, String jdUrl, String roleEmphasis,
            String bulletRanking, List<UUID> selectedBulletIds,
            String coverLetter, List<String> atsMatched, List<String> atsMissing,
            List<String> selectedCourses, Map<String, List<String>> selectedSkills,
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
                    a.getCoverLetter(),
                    Arrays.asList(a.getAtsMatched()), Arrays.asList(a.getAtsMissing()),
                    Arrays.asList(a.getSelectedCourses()), skillsMap,
                    a.getPdfBlob() != null && a.getPdfBlob().length > 0,
                    b64, a.getTectonicLog(), a.getOutcome(), a.getCreatedAt());
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
