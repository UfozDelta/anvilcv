package com.resumepipeline.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public class ResumeDtos {

    public record ParseResumeRequest(@NotBlank @Size(max = 50_000) String text) {}

    public record ParsedExperience(
            String name,
            String title,
            String company,
            String location,
            String dates,
            String description
    ) {}

    public record ParsedProject(
            String name,
            String description,
            String dates
    ) {}

    public record ParseResumeResponse(
            List<ParsedExperience> experiences,
            List<ParsedProject> projects
    ) {}

    public record BulkCreateProjectsRequest(
            @Size(max = 100) @jakarta.validation.Valid List<ProjectDtos.CreateProjectRequest> items
    ) {}
}
