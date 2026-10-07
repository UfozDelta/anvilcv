package com.resumepipeline.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Regression set: a synthetic, fictional bullet bank. Pins which bullets the vanity and padding
 * rules catch, and — just as important — which they must leave alone.
 *
 * Real banks stay out of git: drop exports (same JSON shape) into
 * src/test/resources/bullets/local/ (gitignored) and {@link #localBanksStayNarrow} runs on them.
 */
class BulletBankFixtureTest {

    private static final Path LOCAL = Path.of("src/test/resources/bullets/local");

    static List<Map<String, String>> bank() throws Exception {
        try (InputStream in = BulletBankFixtureTest.class.getResourceAsStream("/bullets/synthetic-bank.json")) {
            return new ObjectMapper().readValue(in, new TypeReference<>() {});
        }
    }

    private static String find(List<Map<String, String>> bank, String fragment) {
        return bank.stream().map(b -> b.get("text")).filter(t -> t.contains(fragment)).findFirst()
                .orElseThrow(() -> new AssertionError("fixture lost bullet: " + fragment));
    }

    private static long vanity(List<Map<String, String>> bank) {
        return bank.stream().filter(b -> BulletTextRules.vanityCount(b.get("text")) != null).count();
    }

    private static long padded(List<Map<String, String>> bank) {
        return bank.stream().filter(b -> BulletTextRules.isPadded(b.get("text"))).count();
    }

    @Test
    void vanityRuleCatchesActivityCounts() throws Exception {
        var bank = bank();
        for (String f : List.of("**120 commits**", "zero broken builds", "**~85 unit tests**",
                "**48 in-repo assert checks**", "**12,000 LOC**", "**9 Flyway migrations**", "**34 source files**")) {
            assertNotNull(BulletTextRules.vanityCount(find(bank, f)), f);
        }
    }

    @Test
    void vanityRuleLeavesDomainScopeAlone() throws Exception {
        var bank = bank();
        for (String f : List.of("**22 REST endpoints**", "**12 pricing strategy classes**", "**3 payment providers**",
                "**8 command types**", "at **16ms** per frame", "**3 regions**")) {
            String t = find(bank, f);
            assertTrue(BulletTextRules.vanityCount(t) == null, t + " -> " + BulletTextRules.vanityCount(t));
        }
    }

    @Test
    void paddingRuleCatchesFillerSecondSentences() throws Exception {
        var bank = bank();
        for (String f : List.of("This architecture enabled rapid", "This commitment to quality",
                "This ensured robust protection", "Developers shipped features faster", "These changes significantly")) {
            assertTrue(BulletTextRules.isPadded(find(bank, f)), f);
        }
    }

    @Test
    void paddingRuleIsNotFooledByAbbreviations() throws Exception {
        var bank = bank();
        for (String f : List.of("in Next.js with", "v3.1 to v5.4", "Postgres vs. SQLite", "i.e. accounts")) {
            assertFalse(BulletTextRules.isPadded(find(bank, f)), f);
        }
    }

    @Test
    void ruleHitRateStaysNarrow() throws Exception {
        var bank = bank();
        // A change here means a rule widened (or narrowed); re-check the must-keep bullets.
        assertEquals(7, vanity(bank));
        assertEquals(5, padded(bank));
    }

    /** Skipped unless real exports sit in the gitignored local/ dir. */
    @Test
    void localBanksStayNarrow() throws Exception {
        List<Path> files;
        try (var s = Files.isDirectory(LOCAL) ? Files.list(LOCAL) : java.util.stream.Stream.<Path>empty()) {
            files = s.filter(p -> p.toString().endsWith(".json")).toList();
        }
        assumeTrue(!files.isEmpty(), "no local bullet banks");
        for (Path f : files) {
            List<Map<String, String>> bank;
            try (InputStream in = Files.newInputStream(f)) {
                bank = new ObjectMapper().readValue(in, new TypeReference<>() {});
            }
            long v = vanity(bank), p = padded(bank);
            System.out.printf("BANK %s bullets=%d vanity=%d padded=%d%n", f.getFileName(), bank.size(), v, p);
            // Ceilings from the 2026-10-06 bank: at most 15 vanity and 12 padded per 196 bullets.
            assertFalse(v > bank.size() * 15 / 196.0, f + " vanity rule hit " + v);
            assertFalse(p > bank.size() * 12 / 196.0, f + " padding rule hit " + p);
        }
    }
}
