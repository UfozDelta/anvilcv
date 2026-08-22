package com.resumepipeline.llm;

import com.resumepipeline.config.GenerationConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.resumepipeline.llm.BulletTextRules.Decision;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    // ---- decide ----

    // Bands are configured in words and converted at CHARS_PER_WORD = 5.4, so the
    // defaults below land at: dead zone 146-216c, floor 65c, single 119-140c,
    // double 227-270c.
    private static GenerationConfig cfg() {
        return new GenerationConfig();
    }

    @Test void decideFilterDisabledKeepsEverything() {
        GenerationConfig c = cfg();
        c.setWordFilterEnabled(false);
        assertEquals(Decision.KEPT, BulletTextRules.decide(1, c));    // would be too short
        assertEquals(Decision.KEPT, BulletTextRules.decide(180, c));  // would be dead zone
    }

    @Test void decideDeadZoneLowBoundary()  { assertEquals(Decision.DEAD_ZONE, BulletTextRules.decide(146, cfg())); }
    @Test void decideDeadZoneHighBoundary() { assertEquals(Decision.DEAD_ZONE, BulletTextRules.decide(216, cfg())); }
    @Test void decideDeadZoneMiddle()       { assertEquals(Decision.DEAD_ZONE, BulletTextRules.decide(180, cfg())); }

    @Test void decideTooShort()             { assertEquals(Decision.TOO_SHORT, BulletTextRules.decide(64, cfg())); }
    @Test void decideFloorBoundaryKept()    { assertEquals(Decision.KEPT, BulletTextRules.decide(65, cfg())); }

    @Test void decideSingleLineKept()    { assertEquals(Decision.KEPT, BulletTextRules.decide(130, cfg())); }
    @Test void decideJustBelowDeadKept() { assertEquals(Decision.KEPT, BulletTextRules.decide(145, cfg())); }
    @Test void decideJustAboveDeadKept() { assertEquals(Decision.KEPT, BulletTextRules.decide(217, cfg())); }
    @Test void decideDoubleLineKept()    { assertEquals(Decision.KEPT, BulletTextRules.decide(248, cfg())); }

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
        assertEquals(List.of("24"), BulletTextRules.fabricatedNumbers("Ran **24** jobs.", SRC));
    }

    @Test void scaledSuffixMatchesSpelledOutSource() {
        // Source says "64,000"; the bullet writes it "64K". Same quantity, must survive.
        assertTrue(BulletTextRules.fabricatedNumbers("Indexed **64K** listings.", SRC).isEmpty());
    }

    @Test void separatorFormMatchesSource() {
        assertTrue(BulletTextRules.fabricatedNumbers("Indexed **64,000** listings.", SRC).isEmpty());
    }

    @Test void scaledSuffixStillCaughtWhenQuantityAbsent() {
        assertEquals(List.of("70K"), BulletTextRules.fabricatedNumbers("Indexed **70K** listings.", SRC));
    }

    @Test void multipleFabricationsEachReported() {
        assertEquals(List.of("500ms", "99%"),
                BulletTextRules.fabricatedNumbers("Cut to **500ms** at **99%** uptime.", SRC));
    }

    @Test void rangeTokenNeedsBothEndsInSource() {
        // "3" is in the source, "180" is not — one bad end condemns the whole token.
        assertEquals(List.of("3 to 180"), BulletTextRules.fabricatedNumbers("Went **3 to 180** ms.", SRC));
    }

    @Test void fabricatedNullAndBlankAreClean() {
        assertTrue(BulletTextRules.fabricatedNumbers(null, SRC).isEmpty());
        assertTrue(BulletTextRules.fabricatedNumbers("   ", SRC).isEmpty());
    }

    @Test void nullSourceMakesEveryNumberFabricated() {
        assertEquals(List.of("5"), BulletTextRules.fabricatedNumbers("Served **5** regions.", null));
    }
}
