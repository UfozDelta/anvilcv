package com.resumepipeline.api;

import com.resumepipeline.auth.AuthUtils;
import com.resumepipeline.config.GenerationConfigService;
import com.resumepipeline.config.GenerationConfigService.GenerationConfigDto;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config/generation")
public class GenerationConfigController {

    private final GenerationConfigService service;

    public GenerationConfigController(GenerationConfigService service) {
        this.service = service;
    }

    @GetMapping
    public GenerationConfigDto get(Authentication auth) {
        return GenerationConfigDto.from(service.get(AuthUtils.userId(auth)));
    }

    @PutMapping
    public GenerationConfigDto update(Authentication auth, @RequestBody GenerationConfigDto dto) {
        return GenerationConfigDto.from(service.update(AuthUtils.userId(auth), dto));
    }
}
