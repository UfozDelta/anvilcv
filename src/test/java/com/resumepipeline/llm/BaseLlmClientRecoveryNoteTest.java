package com.resumepipeline.llm;

import com.resumepipeline.config.GenerationConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The weak-opener repair rewrites "Contributed to" / "Helped with" bullets. Offering a
 * leadership verb as the replacement turns a contribution into a claim of ownership.
 */
class BaseLlmClientRecoveryNoteTest {

    @Test
    void openerRepairNeverOffersLeadershipVerbs() {
        String note = BaseLlmClient.recoveryNote(
                List.of(new BaseLlmClient.Reject("Contributed to the billing service.",
                        BaseLlmClient.RejectReason.OPENER)),
                List.of(), 0, new GenerationConfig());

        assertTrue(note.contains("Contributed to the billing service."));
        assertTrue(note.contains("never turn a"));
        for (String verb : List.of("Led", "Owned", "Architected")) {
            assertFalse(note.matches("(?s).*\\b" + verb + "\\b.*"), "repair offers " + verb);
        }
    }
}
