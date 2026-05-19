package com.resumepipeline.api;

import com.resumepipeline.config.GenerationConfigService;
import com.resumepipeline.config.GenerationConfigService.GenerationConfigDto;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config/generation")
public class GenerationConfigController {

    private final GenerationConfigService service;

    public GenerationConfigController(GenerationConfigService service) {
        this.service = service;
    }

    @GetMapping
    public GenerationConfigDto get() {
        return GenerationConfigDto.from(service.get());
    }

    @PutMapping
    public GenerationConfigDto update(@RequestBody GenerationConfigDto dto) {
        return GenerationConfigDto.from(service.update(dto));
    }
}
