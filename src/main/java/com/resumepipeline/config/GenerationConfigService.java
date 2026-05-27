package com.resumepipeline.config;

import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;

@Service
public class GenerationConfigService {

    private final GenerationConfigRepository repo;

    public GenerationConfigService(GenerationConfigRepository repo) {
        this.repo = repo;
    }

    public GenerationConfig get(UUID userId) {
        return repo.findByUserId(userId).orElseGet(() -> {
            GenerationConfig c = new GenerationConfig();
            c.setId(UUID.randomUUID());
            c.setUserId(userId);
            c.setUpdatedAt(Instant.now());
            return repo.save(c);
        });
    }

    public GenerationConfig update(UUID userId, GenerationConfigDto dto) {
        GenerationConfig c = get(userId);
        c.setWordFilterEnabled(dto.wordFilterEnabled());
        c.setSingleLineLow(dto.singleLineLow());
        c.setSingleLineHigh(dto.singleLineHigh());
        c.setDoubleLineLow(dto.doubleLineLow());
        c.setDoubleLineHigh(dto.doubleLineHigh());
        c.setDeadZoneLow(dto.deadZoneLow());
        c.setDeadZoneHigh(dto.deadZoneHigh());
        c.setMinWordFloor(dto.minWordFloor());
        c.setTemperature(dto.temperature());
        c.setBoldDensity(dto.boldDensity());
        c.setTone(dto.tone());
        c.setActionVerbStyle(dto.actionVerbStyle());
        c.setUpdatedAt(Instant.now());
        return repo.save(c);
    }

    public record GenerationConfigDto(
            boolean wordFilterEnabled,
            int singleLineLow,
            int singleLineHigh,
            int doubleLineLow,
            int doubleLineHigh,
            int deadZoneLow,
            int deadZoneHigh,
            int minWordFloor,
            double temperature,
            GenerationConfig.BoldDensity boldDensity,
            GenerationConfig.Tone tone,
            GenerationConfig.ActionVerbStyle actionVerbStyle
    ) {
        public static GenerationConfigDto from(GenerationConfig c) {
            return new GenerationConfigDto(
                    c.isWordFilterEnabled(),
                    c.getSingleLineLow(), c.getSingleLineHigh(),
                    c.getDoubleLineLow(), c.getDoubleLineHigh(),
                    c.getDeadZoneLow(), c.getDeadZoneHigh(),
                    c.getMinWordFloor(),
                    c.getTemperature(),
                    c.getBoldDensity(),
                    c.getTone(),
                    c.getActionVerbStyle()
            );
        }
    }
}
