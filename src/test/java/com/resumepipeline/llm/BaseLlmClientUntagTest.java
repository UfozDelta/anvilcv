package com.resumepipeline.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Provenance tags are for the human reviewing the extracted context document.
 * They must not reach the model, which pays for them once per category lens.
 */
class BaseLlmClientUntagTest {

    @Test
    void stripsEachTagAndTheSpaceBeforeIt() {
        assertEquals("Pool capped at 4 connections.",
                BaseLlmClient.untag("Pool capped at 4 connections [repo]."));
        assertEquals("77 files fixed in one sweep.",
                BaseLlmClient.untag("77 files fixed in one sweep [commit]."));
        assertEquals("Net minus 314 lines.",
                BaseLlmClient.untag("Net minus 314 lines [diff]."));
        assertEquals("~40K calls/month at peak.",
                BaseLlmClient.untag("~40K calls/month at peak [dev]."));
    }

    @Test
    void stripsRepeatedTagsAcrossOneField() {
        assertEquals("9 entities, 22 migrations, 47 endpoints.",
                BaseLlmClient.untag("9 entities [repo], 22 migrations [repo], 47 endpoints [repo]."));
    }

    @Test
    void leavesUnrelatedBracketsAlone() {
        // Array syntax, citations and log prefixes are content, not provenance.
        String s = "Reads config[0] via a [WARN]-tagged path, per RFC [7231].";
        assertEquals(s, BaseLlmClient.untag(s));
    }

    @Test
    void untaggedTextIsUnchanged() {
        String s = "AES-256-GCM with a 12-byte IV per value.";
        assertEquals(s, BaseLlmClient.untag(s));
    }
}
