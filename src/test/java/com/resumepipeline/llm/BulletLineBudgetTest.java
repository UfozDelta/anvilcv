package com.resumepipeline.llm;

import com.resumepipeline.config.GenerationConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A kept bullet may render one or two lines, never three -- three-line bullets blow the
 * one-page budget the whole selector is built around.
 */
class BulletLineBudgetTest {

    private static String ofChars(int n) {
        // Real words, so wordCount/charCount behave as they do on live text.
        StringBuilder sb = new StringBuilder();
        while (sb.length() < n) sb.append("alpha ");
        return sb.substring(0, n);
    }

    @Test
    void shippedDefaultsCannotAdmitAThirdLine() {
        GenerationConfig cfg = new GenerationConfig();
        int ceiling = BulletTextRules.twoLineCeilingChars(cfg);
        assertTrue(ceiling <= 2 * BulletTextRules.CHARS_PER_LINE,
                "two-line ceiling " + ceiling + " exceeds two rendered lines");
        assertEquals(2, BulletTextRules.estimatedLines(ofChars(ceiling)));
    }

    @Test
    void ceilingIsClampedWhenTheConfiguredBandWouldOverflow() {
        GenerationConfig cfg = new GenerationConfig();
        cfg.setDoubleLineHigh(60);   // 60 words * 7.4 = 444 chars = 5 rendered lines
        assertEquals(2 * BulletTextRules.CHARS_PER_LINE, BulletTextRules.twoLineCeilingChars(cfg));
        assertEquals(BulletTextRules.Decision.TOO_LONG,
                BulletTextRules.decide(2 * BulletTextRules.CHARS_PER_LINE + 1, cfg));
    }

    @Test
    void bandsStillDeriveTheIntendedCharacterTargets() {
        GenerationConfig cfg = new GenerationConfig();
        assertEquals(1, BulletTextRules.estimatedLines(ofChars(BulletTextRules.singleHighChars(cfg))));
        assertEquals(2, BulletTextRules.estimatedLines(ofChars(BulletTextRules.doubleLowChars(cfg))));
    }
}
