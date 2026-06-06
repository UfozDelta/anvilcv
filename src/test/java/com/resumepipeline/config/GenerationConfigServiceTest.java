package com.resumepipeline.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenerationConfigServiceTest {

    @Mock GenerationConfigRepository repo;
    @InjectMocks GenerationConfigService service;

    @Test
    void getReturnsExistingWithoutSaving() {
        UUID user = UUID.randomUUID();
        GenerationConfig existing = new GenerationConfig();
        existing.setUserId(user);
        when(repo.findByUserId(user)).thenReturn(Optional.of(existing));

        assertSame(existing, service.get(user));
        verify(repo, never()).save(any());
    }

    @Test
    void getLazyCreatesDefaultWhenMissing() {
        UUID user = UUID.randomUUID();
        when(repo.findByUserId(user)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GenerationConfig created = service.get(user);

        assertEquals(user, created.getUserId());
        assertNotNull(created.getId());
        assertNotNull(created.getUpdatedAt());
        verify(repo).save(created);
    }

    @Test
    void updateAppliesDtoFieldsRoundTrip() {
        UUID user = UUID.randomUUID();
        GenerationConfig existing = new GenerationConfig();
        existing.setUserId(user);
        when(repo.findByUserId(user)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GenerationConfigService.GenerationConfigDto dto = new GenerationConfigService.GenerationConfigDto(
                false, 20, 25, 40, 48, 26, 39, 10, 0.7,
                GenerationConfig.BoldDensity.HEAVY,
                GenerationConfig.Tone.AGGRESSIVE,
                GenerationConfig.ActionVerbStyle.LEADERSHIP);

        GenerationConfig saved = service.update(user, dto);

        // Round-trip: DTO derived from the saved entity equals the input DTO.
        assertEquals(dto, GenerationConfigService.GenerationConfigDto.from(saved));
    }
}
