package com.resumepipeline.llm;

import com.resumepipeline.config.GenerationConfig;
import org.junit.jupiter.api.Test;

import static com.resumepipeline.llm.BulletTextRules.Decision;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    // ---- decide ----

    private static GenerationConfig cfg() {
        // Defaults: deadZone 27-40, minWordFloor 12, filter enabled.
        return new GenerationConfig();
    }

    @Test void decideFilterDisabledKeepsEverything() {
        GenerationConfig c = cfg();
        c.setWordFilterEnabled(false);
        assertEquals(Decision.KEPT, BulletTextRules.decide(1, c));    // would be too short
        assertEquals(Decision.KEPT, BulletTextRules.decide(30, c));   // would be dead zone
    }

    @Test void decideDeadZoneLowBoundary()  { assertEquals(Decision.DEAD_ZONE, BulletTextRules.decide(27, cfg())); }
    @Test void decideDeadZoneHighBoundary() { assertEquals(Decision.DEAD_ZONE, BulletTextRules.decide(40, cfg())); }
    @Test void decideDeadZoneMiddle()       { assertEquals(Decision.DEAD_ZONE, BulletTextRules.decide(33, cfg())); }

    @Test void decideTooShort()             { assertEquals(Decision.TOO_SHORT, BulletTextRules.decide(11, cfg())); }
    @Test void decideFloorBoundaryKept()    { assertEquals(Decision.KEPT, BulletTextRules.decide(12, cfg())); }

    @Test void decideSingleLineKept()  { assertEquals(Decision.KEPT, BulletTextRules.decide(24, cfg())); } // below dead zone
    @Test void decideJustBelowDeadKept() { assertEquals(Decision.KEPT, BulletTextRules.decide(26, cfg())); }
    @Test void decideJustAboveDeadKept() { assertEquals(Decision.KEPT, BulletTextRules.decide(41, cfg())); }
    @Test void decideDoubleLineKept()  { assertEquals(Decision.KEPT, BulletTextRules.decide(46, cfg())); }

    @Test void decideDeadZoneTakesPrecedenceOverFloor() {
        // A weird config where dead zone overlaps below the floor: dead-zone check runs first.
        GenerationConfig c = cfg();
        c.setDeadZoneLow(5);
        c.setDeadZoneHigh(20);
        c.setMinWordFloor(15);
        assertEquals(Decision.DEAD_ZONE, BulletTextRules.decide(10, c));
    }
}
