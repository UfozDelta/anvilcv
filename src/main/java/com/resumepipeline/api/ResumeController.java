package com.resumepipeline.api;

import com.resumepipeline.api.dto.ProjectDtos.ProjectResponse;
import com.resumepipeline.api.dto.ResumeDtos.BulkCreateProjectsRequest;
import com.resumepipeline.api.dto.ResumeDtos.ParseResumeRequest;
import com.resumepipeline.api.dto.ResumeDtos.ParseResumeResponse;
import com.resumepipeline.auth.AuthUtils;
import com.resumepipeline.project.Project;
import com.resumepipeline.project.ProjectService;
import com.resumepipeline.project.ResumeParserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private final ResumeParserService parser;
    private final ProjectService projects;

    public ResumeController(ResumeParserService parser, ProjectService projects) {
        this.parser = parser;
        this.projects = projects;
    }

    @PostMapping("/parse")
    public ParseResumeResponse parse(@RequestBody @Valid ParseResumeRequest req) {
        return parser.parse(req.text());
    }

    @PostMapping("/import")
    @Transactional
    public List<ProjectResponse> bulkImport(Authentication auth,
                                             @RequestBody @Valid BulkCreateProjectsRequest req) {
        UUID userId = AuthUtils.userId(auth);
        return req.items().stream().map(item -> {
            Project.Kind kind = item.kind() == null ? Project.Kind.PROJECT : item.kind();
            Project p = projects.create(userId, kind, item.name(), item.description(), item.sourcePath(),
                    item.title(), item.company(), item.location(), item.dates());
            return ProjectResponse.from(p);
        }).toList();
    }
}
