package com.resumepipeline.api.dto;

import com.resumepipeline.project.Project;
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
            String description,
            /** The entry's bullet lines, one per bullet, glyphs stripped: what the confirm screen lists. */
            List<String> bullets
    ) {}

    public record ParsedProject(
            String name,
            String description,
            String dates,
            List<String> bullets
    ) {}

    public record ParseResumeResponse(
            List<ParsedExperience> experiences,
            List<ParsedProject> projects
    ) {}

    /** One entry to import. {@code bullets} are the user's own resume lines, confirmed by them; null or empty adds none. */
    public record ImportItem(
            Project.Kind kind,
            @NotBlank String name,
            @NotBlank @Size(max = 20_000) String description,
            String title, String company, String location, String dates,
            @Size(max = 30) List<@NotBlank @Size(max = 600) String> bullets
    ) {}

    public record BulkCreateProjectsRequest(
            @Size(max = 100) @jakarta.validation.Valid List<ImportItem> items
    ) {}
}
