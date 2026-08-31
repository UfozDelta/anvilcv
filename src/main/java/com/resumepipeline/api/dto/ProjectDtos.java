package com.resumepipeline.api.dto;

import com.resumepipeline.project.Project;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public class ProjectDtos {

    public record CreateProjectRequest(
            Project.Kind kind,
            @NotBlank String name,
            @NotBlank String description,
            String githubUrl,
            String title, String company, String location, String dates
    ) {}

    public record UpdateProjectRequest(
            String name,
            String description,
            String githubUrl,
            String techStack,
            String yourRole,
            String ownership,
            String scaleImpact,
            String hardestProblem,
            String technicalDecisions,
            String userImpact,
            String securityPosture,
            String title, String company, String location, String dates
    ) {}

    public record ProjectResponse(
            UUID id,
            Project.Kind kind,
            String name,
            String description,
            String githubUrl,
            boolean repoContextReady,
            String techStack,
            String yourRole,
            String ownership,
            String scaleImpact,
            String hardestProblem,
            String technicalDecisions,
            String userImpact,
            String securityPosture,
            String title, String company, String location, String dates,
            Instant createdAt
    ) {
        public static ProjectResponse from(Project p) {
            return new ProjectResponse(
                    p.getId(), p.getKind(), p.getName(), p.getDescription(),
                    p.getGithubUrl(), p.getRepoContext() != null,
                    p.getTechStack(), p.getYourRole(), p.getOwnership(),
                    p.getScaleImpact(), p.getHardestProblem(),
                    p.getTechnicalDecisions(), p.getUserImpact(), p.getSecurityPosture(),
                    p.getTitle(), p.getCompany(), p.getLocation(), p.getDates(),
                    p.getCreatedAt());
        }
    }
}
