package com.resumepipeline.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseLlmClientCapJdTest {

    @Test
    void underCapPassesThroughUnchanged() {
        String jd = "Senior Java engineer. Spring Boot, Postgres, AWS.";
        assertEquals(jd, BaseLlmClient.capJd(jd));
    }

    @Test
    void atCapPassesThroughUnchanged() {
        String jd = "x".repeat(BaseLlmClient.MAX_JD_CHARS);
        assertEquals(jd, BaseLlmClient.capJd(jd));
    }

    @Test
    void overCapIsTruncatedToAtMostCap() {
        String jd = ("word ".repeat(BaseLlmClient.MAX_JD_CHARS));
        String capped = BaseLlmClient.capJd(jd);
        assertTrue(capped.length() <= BaseLlmClient.MAX_JD_CHARS);
        assertTrue(jd.startsWith(capped));
        assertTrue(capped.length() > BaseLlmClient.MAX_JD_CHARS - 10, "should cut near the cap");
    }

    @Test
    void overCapWithNoWhitespaceFallsBackToHardCut() {
        String jd = "x".repeat(BaseLlmClient.MAX_JD_CHARS + 500);
        assertEquals(BaseLlmClient.MAX_JD_CHARS, BaseLlmClient.capJd(jd).length());
    }

    @Test
    void nullAndBlankAreHandled() {
        assertNull(BaseLlmClient.capJd(null));
        assertEquals("", BaseLlmClient.capJd(""));
        assertEquals("   ", BaseLlmClient.capJd("   "));
    }
}
