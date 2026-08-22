package com.resumepipeline.llm.settings;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LlmSettingsServiceTest {

    private static String randomKey() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }

    private final SecretCipher cipher = new SecretCipher(randomKey());

    private LlmSettingsService serviceOver(LlmSettings row) {
        LlmSettingsRepository repo = mock(LlmSettingsRepository.class);
        when(repo.findFirstByOrderByUpdatedAtAsc()).thenReturn(Optional.of(row));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return new LlmSettingsService(repo, cipher,
                "gemini",
                "yml-gemini-key", "yml-gemini-generate", "yml-gemini-match", "yml-gemini-cleanjd",
                "yml-opencode-key", "https://yml.opencode/v1",
                "yml-opencode-generate", "yml-opencode-match", "yml-opencode-cleanjd",
                "yml-openai-key", "https://yml.openai/v1",
                "yml-openai-generate", "yml-openai-match", "yml-openai-cleanjd");
    }

    private static LlmSettings emptyRow() {
        LlmSettings s = new LlmSettings();
        s.setId(UUID.randomUUID());
        s.setUpdatedAt(Instant.now());
        return s;
    }

    @Test
    void nullColumnsFallBackToYml() {
        LlmSettingsService service = serviceOver(emptyRow());

        LlmSettingsService.Resolved r = service.current().active();

        assertEquals("gemini", r.provider());
        assertEquals("yml-gemini-key", r.apiKey());
        assertEquals("yml-gemini-generate", r.generateModel());
        assertEquals("yml-gemini-match", r.matchModel());
        assertEquals("yml-gemini-cleanjd", r.cleanJdModel());
    }

    @Test
    void dbColumnsWinOverYml() {
        LlmSettings row = emptyRow();
        row.setProvider("opencode");
        row.setOpencodeApiKeyEnc(cipher.encrypt("db-opencode-key"));
        row.setOpencodeModelGenerate("db-generate");

        LlmSettingsService.Resolved r = serviceOver(row).current().active();

        assertEquals("opencode", r.provider());
        assertEquals("db-opencode-key", r.apiKey());
        assertEquals("db-generate", r.generateModel());
        // Untouched columns still fall through to yml.
        assertEquals("yml-opencode-match", r.matchModel());
        assertEquals("https://yml.opencode/v1", r.baseUrl());
    }

    @Test
    void unknownProviderDegradesToGemini() {
        LlmSettings row = emptyRow();
        row.setProvider("not-a-provider");

        assertEquals("gemini", serviceOver(row).current().active().provider());
    }

    @Test
    void blankApiKeyOnUpdateKeepsTheStoredOne() {
        LlmSettings row = emptyRow();
        row.setOpenaiApiKeyEnc(cipher.encrypt("original-key"));
        LlmSettingsService service = serviceOver(row);

        service.update(new LlmSettingsService.UpdateRequest("openai", null, null,
                new LlmSettingsService.ProviderUpdate("  ", null, "gpt-x", null, null)), "admin");

        LlmSettingsService.Resolved r = service.current().active();
        assertEquals("original-key", r.apiKey());
        assertEquals("gpt-x", r.generateModel());
    }

    @Test
    void viewMasksKeysAndNeverReturnsPlaintext() {
        LlmSettings row = emptyRow();
        row.setOpenaiApiKeyEnc(cipher.encrypt("sk-supersecret-tail"));

        LlmSettingsService.View v = serviceOver(row).view();

        assertEquals("...tail", v.openai().apiKeyMasked());
        assertTrue(v.openai().apiKeyFromDb());
        assertFalse(v.gemini().apiKeyFromDb(), "gemini key still comes from yml");
    }

    @Test
    void storedKeyFallsBackToEnvWhenCipherKeyIsMissing() {
        // A deploy that lost LLM_SECRET_KEY must keep running on the env credential
        // rather than throwing on every LLM call.
        LlmSettings row = emptyRow();
        row.setGeminiApiKeyEnc(cipher.encrypt("db-key"));

        LlmSettingsRepository repo = mock(LlmSettingsRepository.class);
        when(repo.findFirstByOrderByUpdatedAtAsc()).thenReturn(Optional.of(row));
        LlmSettingsService service = new LlmSettingsService(repo, new SecretCipher(""),
                "gemini",
                "yml-gemini-key", "g", "m", "c",
                "", "u", "g", "m", "c",
                "", "u", "g", "m", "c");

        assertEquals("yml-gemini-key", service.current().active().apiKey());
    }
}
