package com.resumepipeline.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmUsageServiceTest {

    @Mock LlmUsageLogRepository repo;
    @InjectMocks LlmUsageService service;

    private static TokenAccumulator tokensWith(int prompt, int candidates) {
        TokenAccumulator t = new TokenAccumulator();
        t.add("gemini-2.5-flash", prompt, candidates);
        return t;
    }

    @Test
    void skipsWhenTokensNull() {
        service.record(UUID.randomUUID(), "src", null, null, null);
        verifyNoInteractions(repo);
    }

    @Test
    void skipsWhenZeroPromptTokens() {
        service.record(UUID.randomUUID(), "src", new TokenAccumulator(), null, null);
        verifyNoInteractions(repo);
    }

    @Test
    void savesMappedFields() {
        UUID user = UUID.randomUUID();
        UUID app = UUID.randomUUID();
        service.record(user, "application_pipeline", tokensWith(100, 50), app, null);

        ArgumentCaptor<LlmUsageLog> cap = ArgumentCaptor.forClass(LlmUsageLog.class);
        verify(repo).save(cap.capture());
        LlmUsageLog log = cap.getValue();
        assertEquals(user, log.getUserId());
        assertEquals("application_pipeline", log.getSource());
        assertEquals(100, log.getPromptTokens());
        assertEquals(50, log.getCandidatesTokens());
        assertEquals(app, log.getApplicationId());
    }

    @Test
    void swallowsRepoException() {
        when(repo.save(any())).thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(() ->
                service.record(UUID.randomUUID(), "src", tokensWith(10, 5), null, null));
    }
}
