package com.resumepipeline.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression set: the live bullet bank as of 2026-10-06 (196 bullets, read-only export). Pins
 * which real bullets the vanity and padding rules catch, and — just as important — which real
 * bullets they must leave alone.
 */
class BulletBankFixtureTest {

    static List<Map<String, String>> bank() throws Exception {
        try (InputStream in = BulletBankFixtureTest.class.getResourceAsStream("/bullets/bank-2026-10-06.json")) {
            return new ObjectMapper().readValue(in, new TypeReference<>() {});
        }
    }

    private static String find(List<Map<String, String>> bank, String fragment) {
        return bank.stream().map(b -> b.get("text")).filter(t -> t.contains(fragment)).findFirst()
                .orElseThrow(() -> new AssertionError("fixture lost bullet: " + fragment));
    }

    @Test
    void fixtureIsTheFullBank() throws Exception {
        assertEquals(196, bank().size());
    }

    @Test
    void vanityRuleCatchesActivityCounts() throws Exception {
        var bank = bank();
        for (String f : List.of("with 87 commits", "zero broken main builds", "~192 unit tests",
                "156 in-repo assert checks")) {
            assertNotNull(BulletTextRules.vanityCount(find(bank, f)), f);
        }
    }

    @Test
    void vanityRuleLeavesDomainScopeAlone() throws Exception {
        var bank = bank();
        for (String f : List.of("16 indicator/filter classes", "Integrated **4 distinct venues**",
                "Designed **41** REST endpoints", "two-channel backpressure model for **15 commands**",
                "rendering **HTML5 Canvas** panes at **16.7ms**")) {
            String t = find(bank, f);
            assertTrue(BulletTextRules.vanityCount(t) == null, t + " -> " + BulletTextRules.vanityCount(t));
        }
    }

    @Test
    void paddingRuleCatchesFillerSecondSentences() throws Exception {
        var bank = bank();
        for (String f : List.of("This architecture enabled rapid", "This commitment to quality",
                "This ensured robust protection")) {
            assertTrue(BulletTextRules.isPadded(find(bank, f)), f);
        }
    }

    @Test
    void ruleHitRateStaysNarrow() throws Exception {
        var bank = bank();
        long vanity = bank.stream().filter(b -> BulletTextRules.vanityCount(b.get("text")) != null).count();
        long padded = bank.stream().filter(b -> BulletTextRules.isPadded(b.get("text"))).count();
        // 12 and 9 when written; a jump means a rule widened and started eating real bullets.
        assertFalse(vanity > 15, "vanity rule hit " + vanity);
        assertFalse(padded > 12, "padding rule hit " + padded);
    }
}
