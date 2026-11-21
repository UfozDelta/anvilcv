package com.resumepipeline.api;

import com.resumepipeline.api.dto.AgentDtos.BulletChecksResponse;
import com.resumepipeline.api.dto.AgentDtos.BulletsResponse;
import com.resumepipeline.api.dto.AgentDtos.CheckBulletsRequest;
import com.resumepipeline.api.dto.AgentDtos.CheckCoverageRequest;
import com.resumepipeline.api.dto.AgentDtos.CoverageResponse;
import com.resumepipeline.api.dto.AgentDtos.CreateAgentApplicationRequest;
import com.resumepipeline.api.dto.AgentDtos.CreateBulletsRequest;
import com.resumepipeline.api.dto.AgentDtos.CreateStoryRequest;
import com.resumepipeline.api.dto.AgentDtos.StoryResponse;
import com.resumepipeline.api.dto.AgentDtos.UpdateAgentApplicationRequest;
import com.resumepipeline.api.dto.ApplicationDtos.ApplicationResponse;
import com.resumepipeline.application.Application;
import com.resumepipeline.auth.AuthUtils;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * External-agent bridge. All generation and judging happens in the calling agent; these
 * endpoints only run AnvilCV's deterministic checks and persist through AnvilCV's services.
 */
@RestController
@RequestMapping("/api/agent")
public class AgentBridgeController {

    private final AgentBridgeService service;

    public AgentBridgeController(AgentBridgeService service) {
        this.service = service;
    }

    @PostMapping("/stories")
    public StoryResponse createStory(Authentication auth, @RequestBody @Valid CreateStoryRequest req) {
        return new StoryResponse(service.createStory(AuthUtils.userId(auth), req));
    }

    @PostMapping("/stories/{storyId}/bullets")
    public BulletsResponse createBullets(Authentication auth, @PathVariable UUID storyId,
                                         @RequestBody @Valid CreateBulletsRequest req) {
        return new BulletsResponse(service.createBullets(AuthUtils.userId(auth), storyId, req));
    }

    @PostMapping("/checks/bullets")
    public BulletChecksResponse checkBullets(Authentication auth, @RequestBody @Valid CheckBulletsRequest req) {
        return service.checkBullets(AuthUtils.userId(auth), req);
    }

    @PostMapping("/checks/coverage")
    public CoverageResponse checkCoverage(Authentication auth, @RequestBody @Valid CheckCoverageRequest req) {
        // No ownership needed: the agent scores its own draft JD/text pair.
        return service.checkCoverage(req);
    }

    @PostMapping("/applications")
    public ApplicationResponse createApplication(Authentication auth,
                                                 @RequestBody @Valid CreateAgentApplicationRequest req) {
        Application saved = service.createApplication(AuthUtils.userId(auth), req);
        return ApplicationResponse.from(saved);
    }

    @PatchMapping("/applications/{id}")
    public ApplicationResponse updateScoring(Authentication auth, @PathVariable UUID id,
                                             @RequestBody @Valid UpdateAgentApplicationRequest req) {
        return ApplicationResponse.from(service.updateScoring(AuthUtils.userId(auth), id, req));
    }
}
