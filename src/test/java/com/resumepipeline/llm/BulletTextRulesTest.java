package com.resumepipeline.llm;

import com.resumepipeline.config.GenerationConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.resumepipeline.llm.BulletTextRules.Decision;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulletTextRulesTest {

    // ---- wordCount ----

    @Test void wordCountNull()  { assertEquals(0, BulletTextRules.wordCount(null)); }
    @Test void wordCountBlank() { assertEquals(0, BulletTextRules.wordCount("   ")); }
    @Test void wordCountPlain() { assertEquals(3, BulletTextRules.wordCount("one two three")); }

    @Test void wordCountStripsBoldMarkup() {
        // **64K** is one word, not three tokens.
        assertEquals(2, BulletTextRules.wordCount("Built **64K**"));
    }

    @Test void wordCountCollapsesWhitespace() {
        assertEquals(3, BulletTextRules.wordCount("  one   two\tthree  "));
    }

    // ---- ensureTerminalPeriod ----

    @Test void periodNull()       { assertEquals("", BulletTextRules.ensureTerminalPeriod(null)); }
    @Test void periodBlank()      { assertEquals("", BulletTextRules.ensureTerminalPeriod("   ")); }
    @Test void periodAdded()      { assertEquals("Built it.", BulletTextRules.ensureTerminalPeriod("Built it")); }
    @Test void periodTrimsFirst() { assertEquals("Built it.", BulletTextRules.ensureTerminalPeriod("  Built it  ")); }
    @Test void periodKeptDot()    { assertEquals("Built it.", BulletTextRules.ensureTerminalPeriod("Built it.")); }
    @Test void periodKeptBang()   { assertEquals("Built it!", BulletTextRules.ensureTerminalPeriod("Built it!")); }
    @Test void periodKeptQ()      { assertEquals("Why?", BulletTextRules.ensureTerminalPeriod("Why?")); }

    // ---- charCount ----

    @Test void charCountNull()  { assertEquals(0, BulletTextRules.charCount(null)); }
    @Test void charCountBlank() { assertEquals(0, BulletTextRules.charCount("   ")); }
    @Test void charCountPlain() { assertEquals(11, BulletTextRules.charCount("Built a RAG")); }

    @Test void charCountStripsBoldMarkers() {
        // The ** markers compile to \textbf{} and take no width on the page.
        assertEquals(11, BulletTextRules.charCount("Built a **RAG**"));
    }

    @Test void charCountSeparatesWhatWordCountConflates() {
        // The whole point of the switch: one "word" each, wildly different line fill.
        assertEquals(1, BulletTextRules.wordCount("**a**"));
        assertEquals(1, BulletTextRules.wordCount("**Kubernetes**"));
        assertEquals(1, BulletTextRules.charCount("**a**"));
        assertEquals(10, BulletTextRules.charCount("**Kubernetes**"));
    }

    // ---- estimatedLines ----

    @Test void estimatedLinesOneLine() {
        // ~90 chars fits in one 105-char line.
        assertEquals(1, BulletTextRules.estimatedLines("x".repeat(90)));
    }

    @Test void estimatedLinesTwoLines() {
        // ~200 chars needs two 105-char lines.
        assertEquals(2, BulletTextRules.estimatedLines("x".repeat(200)));
    }

    @Test void estimatedLinesFourLines() {
        // The real over-long bullet class from the shipped PDF: ~320 chars -> 4 lines.
        assertEquals(4, BulletTextRules.estimatedLines("x".repeat(320)));
    }

    @Test void estimatedLinesStripsBoldMarkers() {
        assertEquals(1, BulletTextRules.estimatedLines("**" + "x".repeat(90) + "**"));
    }

    @Test void estimatedLinesEmptyIsStillOneLine() {
        assertEquals(1, BulletTextRules.estimatedLines(""));
        assertEquals(1, BulletTextRules.estimatedLines(null));
    }

    // ---- decide ----

    // Bands are configured in words and converted at CHARS_PER_WORD = 5.4, so the
    // defaults below land at: dead zone 103-167c, floor 65c, single 81-97c,
    // double 173-200c. Calibrated so single/double really mean one/two rendered
    // lines at CHARS_PER_LINE = 105 (see BulletTextRules).
    private static GenerationConfig cfg() {
        return new GenerationConfig();
    }

    @Test void decideFilterDisabledKeepsEverything() {
        GenerationConfig c = cfg();
        c.setWordFilterEnabled(false);
        assertEquals(Decision.KEPT, BulletTextRules.decide(1, c));    // would be too short
        assertEquals(Decision.KEPT, BulletTextRules.decide(130, c));  // would be dead zone
    }

    @Test void decideDeadZoneLowBoundary()  { assertEquals(Decision.DEAD_ZONE, BulletTextRules.decide(103, cfg())); }
    @Test void decideDeadZoneHighBoundary() { assertEquals(Decision.DEAD_ZONE, BulletTextRules.decide(167, cfg())); }
    @Test void decideDeadZoneMiddle()       { assertEquals(Decision.DEAD_ZONE, BulletTextRules.decide(130, cfg())); }

    @Test void decideTooShort()             { assertEquals(Decision.TOO_SHORT, BulletTextRules.decide(64, cfg())); }
    @Test void decideFloorBoundaryKept()    { assertEquals(Decision.KEPT, BulletTextRules.decide(65, cfg())); }

    @Test void decideSingleLineKept()    { assertEquals(Decision.KEPT, BulletTextRules.decide(90, cfg())); }
    @Test void decideJustBelowDeadKept() { assertEquals(Decision.KEPT, BulletTextRules.decide(102, cfg())); }
    @Test void decideJustAboveDeadKept() { assertEquals(Decision.KEPT, BulletTextRules.decide(168, cfg())); }
    @Test void decideDoubleLineKept()    { assertEquals(Decision.KEPT, BulletTextRules.decide(185, cfg())); }

    @Test void decideCeilingBoundaryKept()  { assertEquals(Decision.KEPT, BulletTextRules.decide(200, cfg())); }
    @Test void decideJustOverCeiling()      { assertEquals(Decision.TOO_LONG, BulletTextRules.decide(201, cfg())); }
    @Test void decideWayOverCeiling()       { assertEquals(Decision.TOO_LONG, BulletTextRules.decide(400, cfg())); }

    // ---- decide with the new defaults, against realistic bullet lengths ----

    @Test void decideRealisticShortBulletKept() {
        // A 95-char bullet (roughly one CHARS_PER_LINE-worth) is kept.
        assertEquals(Decision.KEPT, BulletTextRules.decide(95, cfg()));
    }

    @Test void decideRealisticOverlongBulletTooLong() {
        // The real over-long bullet class from the shipped PDF: ~320 chars, 4 rendered lines.
        String bullet = "x".repeat(320);
        assertEquals(Decision.TOO_LONG, BulletTextRules.decide(bullet.length(), cfg()));
    }

    @Test void decideFilterDisabledKeepsOverlongBullet() {
        // The ceiling is part of the filter, so disabling the filter must bypass it too.
        GenerationConfig c = cfg();
        c.setWordFilterEnabled(false);
        assertEquals(Decision.KEPT, BulletTextRules.decide(400, c));
    }

    @Test void decideDeadZoneTakesPrecedenceOverFloor() {
        // A weird config where dead zone overlaps below the floor: dead-zone check runs first.
        // 5-20 words is 27-108 chars, floor 15 words is 81 chars; 54 chars sits in both.
        GenerationConfig c = cfg();
        c.setDeadZoneLow(5);
        c.setDeadZoneHigh(20);
        c.setMinWordFloor(15);
        assertEquals(Decision.DEAD_ZONE, BulletTextRules.decide(54, c));
    }

    // ---- fabricatedNumbers ----

    private static final String SRC = "Served 5 regions over 64,000 listings across 3 tenants in 2024.";

    @Test void fabricatedNoBoldIsClean() {
        assertTrue(BulletTextRules.fabricatedNumbers("Built a thing.", SRC).isEmpty());
    }

    @Test void fabricatedBoldWithoutNumbersIsClean() {
        assertTrue(BulletTextRules.fabricatedNumbers("Built **Kubernetes** tooling.", SRC).isEmpty());
    }

    @Test void quotedNumberIsKept() {
        assertTrue(BulletTextRules.fabricatedNumbers("Served **5** regions.", SRC).isEmpty());
    }

    // The old substring check let any superset of a source digit run through: source "5"
    // vouched for "500ms", "95" and "15+". These are the regressions that matter most.
    @Test void supersetOfSourceNumberIsFabricated() {
        assertEquals(List.of("500ms"), BulletTextRules.fabricatedNumbers("Cut latency to **500ms**.", SRC));
    }

    @Test void otherSupersetsOfSourceNumberAreFabricated() {
        assertEquals(List.of("95%"), BulletTextRules.fabricatedNumbers("Hit **95%** coverage.", SRC));
        assertEquals(List.of("15+"), BulletTextRules.fabricatedNumbers("Shipped **15+** features.", SRC));
    }

    // The mirror case: source "2024" used to vouch for "24" and "202".
    @Test void substringOfSourceNumberIsFabricated() {
        assertEquals(List.of("24K"), BulletTextRules.fabricatedNumbers("Ran **24K** jobs.", SRC));
    }

    @Test void scaledSuffixMatchesSpelledOutSource() {
        // Source says "64,000"; the bullet writes it "64K". Same quantity, must survive.
        assertTrue(BulletTextRules.fabricatedNumbers("Indexed **64K** listings.", SRC).isEmpty());
    }

    @Test void separatorFormMatchesSource() {
        assertTrue(BulletTextRules.fabricatedNumbers("Indexed **64,000+** listings.", SRC).isEmpty());
    }

    @Test void scaledSuffixStillCaughtWhenQuantityAbsent() {
        assertEquals(List.of("70K"), BulletTextRules.fabricatedNumbers("Indexed **70K** listings.", SRC));
    }

    @Test void multipleFabricationsEachReported() {
        assertEquals(List.of("500ms", "99%"),
                BulletTextRules.fabricatedNumbers("Cut to **500ms** at **99%** uptime.", SRC));
    }

    @Test void rangeTokenNeedsBothEndsInSource() {
        // Only "180" carries a unit (" ms"), so only "180" is checked — and it is absent
        // from the source. The bare "3" is not a claim and is skipped.
        assertEquals(List.of("180"), BulletTextRules.fabricatedNumbers("Went **3 to 180** ms.", SRC));
    }

    @Test void fabricatedNullAndBlankAreClean() {
        assertTrue(BulletTextRules.fabricatedNumbers(null, SRC).isEmpty());
        assertTrue(BulletTextRules.fabricatedNumbers("   ", SRC).isEmpty());
    }

    @Test void nullSourceMakesEveryNumberFabricated() {
        assertEquals(List.of("5M"), BulletTextRules.fabricatedNumbers("Served **5M** requests.", null));
    }

    // The hole this check exists to close: bolding is style-configurable, so an invented
    // metric that was never bolded used to pass untouched.
    @Test void unboldedFabricatedMetricIsCaught() {
        assertEquals(List.of("500ms"), BulletTextRules.fabricatedNumbers("Cut latency to 500ms.", SRC));
        assertEquals(List.of("99%"), BulletTextRules.fabricatedNumbers("Held 99% uptime.", SRC));
    }

    // Version and product identifiers are not claims, even though none of these digits
    // appear in the source.
    @Test void versionAndProductNumbersAreNotQuantities() {
        assertTrue(BulletTextRules.fabricatedNumbers(
                "Shipped AES-256-GCM over a 3-tier Java 17 stack running 24/7 on S3.", SRC).isEmpty());
    }

    // Regression: "K8s" parsed as "8" + the "s" time unit, so a clean Kubernetes bullet was
    // discarded as a fabricated metric. A digit welded to a preceding letter is a product name.
    @Test void digitGluedToLeadingLetterIsNotAQuantity() {
        assertTrue(BulletTextRules.fabricatedNumbers(
                "Deployed on K8s across EC2 with P95 tracking on ES2022.", "No digits here.").isEmpty());
    }

    @Test void currencyAmountIsCaught() {
        assertEquals(List.of("$200K"), BulletTextRules.fabricatedNumbers("Saved $200K yearly.", SRC));
    }

    // Previously a false positive: "3-tier" was a bold token containing a number, so the
    // whole token was condemned whenever "3" was missing from the source.
    @Test void boldedTierCountIsNotAQuantity() {
        assertTrue(BulletTextRules.fabricatedNumbers("Built a **3-tier** service.", "No digits here.").isEmpty());
    }

    // ---- isNearDuplicate: quantity-overlap signal ----
    //
    // The real shipped-PDF pair that motivated this: reworded enough that Jaccard word-overlap
    // alone (0.386) sits well under NEAR_DUPLICATE_THRESHOLD (0.6), yet both bullets assert the
    // same four numbers (6,062 / 9 / 14 / 46.44%). isNearDuplicate must catch this via the
    // quantity-overlap path even though the classic word-overlap path misses it.

    private static final String REAL_A = "Engineered a full backtesting infrastructure for a "
            + "systematic equity index, comprising 6,062 lines of Python across 9 modules and "
            + "14 classes, leveraging pandas and numpy. This system enabled comprehensive "
            + "strategy evaluation, yielding 46.44% cumulative return and a Sharpe 2.976 in "
            + "backtest over a 10-month period, outperforming benchmarks.";
    private static final String REAL_B = "Architected a full backtesting infrastructure, "
            + "spanning 6,062 lines of Python across 9 modules and 14 classes, supporting a "
            + "15-ticker multi-region universe and comprehensive trade analytics, delivering "
            + "46.44% cumulative return in backtest.";

    @Test void realShippedPdfPairIsFlaggedDespiteLowWordOverlap() {
        double sim = BulletTextRules.similarity(REAL_A, REAL_B);
        assertTrue(sim < BulletTextRules.NEAR_DUPLICATE_THRESHOLD,
                "word-overlap alone must NOT catch this pair (got " + sim + "), or this test no longer proves anything");
        assertTrue(sim >= BulletTextRules.QUANTITY_DUPLICATE_THRESHOLD,
                "still needs to clear the lower quantity-overlap Jaccard floor, got " + sim);
        assertTrue(BulletTextRules.isNearDuplicate(REAL_A, Set.of(REAL_B)),
                "same four quantities (6062/9/14/46.44) restated across reworded prose must be flagged");
    }

    @Test void classicWordOverlapDuplicateStillCaught() {
        // Unchanged behavior: near-identical prose with no numbers at all still trips the
        // original Jaccard check on its own.
        assertTrue(BulletTextRules.isNearDuplicate(
                "Built a REST API in Spring Boot with Redis caching and PostgreSQL persistence.",
                Set.of("Built a REST API using Spring Boot with Redis caching and PostgreSQL persistence layer.")));
    }

    @Test void oneSharedQuantityInSameDomainIsNotFlagged() {
        // Same rough shape and subject area (Jaccard 0.5, comfortably inside the [0.3, 0.6)
        // quantity-check band — high enough that the pair would wrongly collide if the
        // two-quantity floor weren't there), but only one incidental shared number ("3") and
        // genuinely different work (onboarding vs. checkout). One shared quantity must not be
        // enough on its own — the two-quantity floor exists precisely to let this through.
        String a = "Led a team of 3 engineers to deliver a new customer onboarding flow, "
                + "reducing signup time by half.";
        String b = "Led a team of 3 designers to deliver a new checkout experience, "
                + "reducing cart abandonment by half.";
        double sim = BulletTextRules.similarity(a, b);
        assertTrue(sim >= BulletTextRules.QUANTITY_DUPLICATE_THRESHOLD && sim < BulletTextRules.NEAR_DUPLICATE_THRESHOLD,
                "fixture must sit inside the quantity-check band to actually exercise the floor, got " + sim);
        assertFalse(BulletTextRules.isNearDuplicate(b, Set.of(a)),
                "one shared quantity in a loosely related pair must not trigger the duplicate signal");
    }

    @Test void sharedQuantitiesInUnrelatedSubjectAreasNotFlagged() {
        // Two bullets that happen to share several numbers but are about completely different
        // work (Jaccard well under 0.3) must not be flagged — the quantity floor alone isn't
        // sufficient; the pair also has to be in the same rough subject area.
        String a = "Optimized a database query from 46 seconds to 14 seconds for 9 tenants.";
        String b = "Wrote 9 Terraform modules provisioning 14 VPCs across 46 AWS accounts for onboarding.";
        double sim = BulletTextRules.similarity(a, b);
        assertTrue(sim < BulletTextRules.QUANTITY_DUPLICATE_THRESHOLD,
                "these must fall below the quantity-overlap Jaccard floor, got " + sim);
        assertFalse(BulletTextRules.isNearDuplicate(a, Set.of(b)));
    }

    // ---- capBoldSpans ----

    // The real shipped-PDF bullet this cap exists for: every noun and number bolded, so
    // nothing reads as emphasized.
    private static final String OVERBOLDED_REAL_BULLET = "Designed **L2 order book maintenance** for "
            + "**4 venues** using **Python**, implementing **Binance diff-depth U/u/pu sequence "
            + "continuity** sync at an undocumented **0ms** cadence and **1e8 scaled-integer price "
            + "keys**. This delivered exact level equality and robust data quality via a "
            + "**LIVE/RESYNCING/STALE** freshness state machine, preventing stale metric emission.";

    private static long boldSpanCount(String text) {
        return java.util.regex.Pattern.compile("\\*\\*(.+?)\\*\\*").matcher(text).results().count();
    }

    @Test void capBoldSpansRealBulletKeepsOnlyTwoDigitBearingSpans() {
        String capped = BulletTextRules.capBoldSpans(OVERBOLDED_REAL_BULLET);
        assertEquals(2, boldSpanCount(capped), "capped bullet: " + capped);

        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\*\\*(.+?)\\*\\*").matcher(capped);
        while (m.find()) {
            assertTrue(m.group(1).chars().anyMatch(Character::isDigit),
                    "surviving span should be digit-bearing: " + m.group(1));
        }

        // Unwrapped, not deleted: every word survives, only markup differs.
        assertEquals(OVERBOLDED_REAL_BULLET.replace("**", ""), capped.replace("**", ""));
    }

    @Test void capBoldSpansLeavesTwoSpansUnchanged() {
        String text = "Built **Kubernetes** tooling handling **500ms** latency.";
        assertEquals(text, BulletTextRules.capBoldSpans(text));
    }

    @Test void capBoldSpansWithNoDigitsKeepsEarliestTwo() {
        String text = "Used **Python** and **Java** and **Go** tools.";
        String expected = "Used **Python** and **Java** and Go tools.";
        assertEquals(expected, BulletTextRules.capBoldSpans(text));
    }

    @Test void capBoldSpansUnpairedMarkerDoesNotThrowOrMangle() {
        String text = "Weird ** markup here with no closing pair.";
        assertEquals(text, BulletTextRules.capBoldSpans(text));
    }

    @Test void capBoldSpansNullAndBlankPassThrough() {
        assertEquals(null, BulletTextRules.capBoldSpans(null));
        assertEquals("   ", BulletTextRules.capBoldSpans("   "));
    }

    // HEAVY density is a real user choice, so it must survive the cap rather than be
    // silently flattened to the default 2 — see BulletTextRules.maxBoldSpans.
    @Test void heavyDensityKeepsFourBoldSpans() {
        String text = "Cut **p99 latency** by **40%** across **12 services** using **Redis** and **Kafka**.";
        String capped = BulletTextRules.capBoldSpans(text, BulletTextRules.MAX_BOLD_SPANS_HEAVY);
        assertEquals(4, boldSpanCount(capped), capped);
        assertEquals(2, boldSpanCount(BulletTextRules.capBoldSpans(text)), "default ceiling unchanged");
    }

    @Test void maxBoldSpansFollowsConfiguredDensity() {
        GenerationConfig heavy = new GenerationConfig();
        heavy.setBoldDensity(GenerationConfig.BoldDensity.HEAVY);
        assertEquals(BulletTextRules.MAX_BOLD_SPANS_HEAVY, BulletTextRules.maxBoldSpans(heavy));

        GenerationConfig light = new GenerationConfig();
        light.setBoldDensity(GenerationConfig.BoldDensity.LIGHT);
        assertEquals(BulletTextRules.MAX_BOLD_SPANS, BulletTextRules.maxBoldSpans(light));
        assertEquals(BulletTextRules.MAX_BOLD_SPANS, BulletTextRules.maxBoldSpans(null));
    }

    @Test void capBoldSpansDoesNotChangeCharCount() {
        // The length filter measures charCount() with ** stripped — capping bold must not
        // move a bullet across a keep/drop boundary.
        int before = BulletTextRules.charCount(OVERBOLDED_REAL_BULLET);
        int after = BulletTextRules.charCount(BulletTextRules.capBoldSpans(OVERBOLDED_REAL_BULLET));
        assertEquals(before, after);
    }
}
