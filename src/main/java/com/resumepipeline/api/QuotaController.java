package com.resumepipeline.api;

import com.resumepipeline.auth.AuthUtils;
import com.resumepipeline.quota.QuotaService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What the signed-in user's plan allows and how much of it is used, for the meter and upgrade prompts. */
@RestController
@RequestMapping("/api/quota")
public class QuotaController {

    private final QuotaService quota;

    public QuotaController(QuotaService quota) {
        this.quota = quota;
    }

    @GetMapping
    public QuotaService.Usage usage(Authentication auth) {
        return quota.usage(AuthUtils.userId(auth));
    }
}
