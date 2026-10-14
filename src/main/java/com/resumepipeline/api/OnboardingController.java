package com.resumepipeline.api;

import com.resumepipeline.auth.AuthUtils;
import com.resumepipeline.onboarding.OnboardingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Progress through the guided first run, and the two events that move it. */
@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {

    private final OnboardingService onboarding;

    public OnboardingController(OnboardingService onboarding) {
        this.onboarding = onboarding;
    }

    @GetMapping
    public OnboardingService.Status status(Authentication auth) {
        return onboarding.status(AuthUtils.userId(auth));
    }

    @PostMapping("/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(Authentication auth) {
        onboarding.complete(AuthUtils.userId(auth));
    }

    @PostMapping("/downloaded")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void downloaded(Authentication auth) {
        onboarding.downloaded(AuthUtils.userId(auth));
    }
}
