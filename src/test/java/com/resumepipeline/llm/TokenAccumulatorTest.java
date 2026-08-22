package com.resumepipeline.llm;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenAccumulatorTest {

    @Test
    void freeModelsCountTokensButCostNothing() {
        TokenAccumulator tokens = new TokenAccumulator();
        tokens.add("x-preview-f-free", 1_000, 500);

        assertEquals(1_000, tokens.getPromptTokens());
        assertEquals(500, tokens.getCandidatesTokens());
        assertEquals(0, tokens.getCostUsd().compareTo(BigDecimal.ZERO));
    }

    @Test
    void paidModelsStillPriced() {
        TokenAccumulator tokens = new TokenAccumulator();
        tokens.add("gemini-2.5-flash", 1_000, 500);

        assertTrue(tokens.getCostUsd().compareTo(BigDecimal.ZERO) > 0);
    }
}
