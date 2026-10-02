package com.resumepipeline.api.dto;

import com.resumepipeline.project.Project;
import com.resumepipeline.render.TechStackSummary;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ProjectDtos {

    public record CreateProjectRequest(
            Project.Kind kind,
            @NotBlank String name,
            @NotBlank String description,
            String githubUrl,
            String title, String company, String location, String dates,
            Boolean current
    ) {}

    public record UpdateProjectRequest(
            String name,
            String description,
            String contextDescription,
            String githubUrl,
            String techStack,
            String yourRole,
            String ownership,
            String scaleImpact,
            String hardestProblem,
            String technicalDecisions,
            String userImpact,
            String securityPosture,
            String title, String company, String location, String dates,
            Boolean current
    ) {}

    public record ProjectResponse(
            UUID id,
            Project.Kind kind,
            String name,
            String description,
            String contextDescription,
            String githubUrl,
            boolean repoContextReady,
            String repoBranch,
            String repoCommitSha,
            String techStack,
            List<String> techTerms,
            String yourRole,
            String ownership,
            String scaleImpact,
            String hardestProblem,
            String technicalDecisions,
            String userImpact,
            String securityPosture,
            String title, String company, String location, String dates,
            Instant createdAt,
            Instant updatedAt,
            long bulletCount,
            boolean current
    ) {
        /** For callers that don't have a bullet count at hand (a just-created project has none). */
        public static ProjectResponse from(Project p) {
            return from(p, 0);
        }

        public static ProjectResponse from(Project p, long bulletCount) {
            return new ProjectResponse(
                    p.getId(), p.getKind(), p.getName(), p.getDescription(), p.getContextDescription(),
                    p.getGithubUrl(), p.getRepoContext() != null,
                    p.getRepoBranch(), p.getRepoCommitSha(),
                    p.getTechStack(), TechStackSummary.matchAll(p.getTechStack()), p.getYourRole(), p.getOwnership(),
                    p.getScaleImpact(), p.getHardestProblem(),
                    p.getTechnicalDecisions(), p.getUserImpact(), p.getSecurityPosture(),
                    p.getTitle(), p.getCompany(), p.getLocation(), p.getDates(),
                    p.getCreatedAt(), p.getUpdatedAt(), bulletCount, p.isCurrent());
        }
    }
}
