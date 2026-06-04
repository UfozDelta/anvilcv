package com.resumepipeline.llm;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe token counter for one pipeline run.
 * Pricing as of 2025-06 for Gemini 2.5 Flash (≤200k context window):
 *   input  $0.15 / 1M tokens
 *   output $3.50 / 1M tokens
 * Flash-lite rates:
 *   input  $0.10 / 1M tokens
 *   output $0.40 / 1M tokens
 */
public class TokenAccumulator {

    private static final BigDecimal FLASH_IN_PER_TOKEN  = new BigDecimal("0.00000015");
    private static final BigDecimal FLASH_OUT_PER_TOKEN = new BigDecimal("0.0000035");
    private static final BigDecimal LITE_IN_PER_TOKEN   = new BigDecimal("0.0000001");
    private static final BigDecimal LITE_OUT_PER_TOKEN  = new BigDecimal("0.0000004");

    private final AtomicInteger promptTokens     = new AtomicInteger(0);
    private final AtomicInteger candidatesTokens = new AtomicInteger(0);
    private BigDecimal costUsd = BigDecimal.ZERO;
    private final Object costLock = new Object();

    public void add(String model, int prompt, int candidates) {
        promptTokens.addAndGet(prompt);
        candidatesTokens.addAndGet(candidates);

        boolean isLite = model != null && model.contains("lite");
        BigDecimal inRate  = isLite ? LITE_IN_PER_TOKEN  : FLASH_IN_PER_TOKEN;
        BigDecimal outRate = isLite ? LITE_OUT_PER_TOKEN : FLASH_OUT_PER_TOKEN;

        BigDecimal callCost = inRate.multiply(BigDecimal.valueOf(prompt))
                .add(outRate.multiply(BigDecimal.valueOf(candidates)));

        synchronized (costLock) {
            costUsd = costUsd.add(callCost);
        }
    }

    public int getPromptTokens()     { return promptTokens.get(); }
    public int getCandidatesTokens() { return candidatesTokens.get(); }
    public BigDecimal getCostUsd()   {
        synchronized (costLock) {
            return costUsd.setScale(8, RoundingMode.HALF_UP);
        }
    }
}
