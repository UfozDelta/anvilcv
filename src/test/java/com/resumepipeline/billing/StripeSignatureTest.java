package com.resumepipeline.billing;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class StripeSignatureTest {

    static final String SECRET = "whsec_test";
    static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    static final byte[] BODY = "{\"id\":\"evt_1\"}".getBytes(StandardCharsets.UTF_8);

    static String header(long t, byte[] body, String secret) {
        return "t=" + t + ",v1=" + HexFormat.of().formatHex(StripeSignature.hmac(secret, String.valueOf(t), body));
    }

    @Test
    void acceptsAValidSignature() {
        assertTrue(StripeSignature.valid(BODY, header(NOW.getEpochSecond(), BODY, SECRET), SECRET, NOW));
    }

    @Test
    void rejectsATamperedBody() {
        String h = header(NOW.getEpochSecond(), BODY, SECRET);
        assertFalse(StripeSignature.valid("{\"id\":\"evt_2\"}".getBytes(StandardCharsets.UTF_8), h, SECRET, NOW));
    }

    @Test
    void rejectsTheWrongSecret() {
        assertFalse(StripeSignature.valid(BODY, header(NOW.getEpochSecond(), BODY, "other"), SECRET, NOW));
    }

    @Test
    void rejectsAStaleTimestamp() {
        long old = NOW.getEpochSecond() - StripeSignature.TOLERANCE_SECONDS - 1;
        assertFalse(StripeSignature.valid(BODY, header(old, BODY, SECRET), SECRET, NOW));
    }

    @Test
    void acceptsAnyMatchingV1WhenSeveralAreSent() {
        long t = NOW.getEpochSecond();
        String h = "t=" + t + ",v1=" + "00".repeat(32) + "," + header(t, BODY, SECRET).substring(("t=" + t + ",").length());
        assertTrue(StripeSignature.valid(BODY, h, SECRET, NOW));
    }

    @Test
    void rejectsMissingOrMalformedHeadersAndAnUnsetSecret() {
        assertFalse(StripeSignature.valid(BODY, null, SECRET, NOW));
        assertFalse(StripeSignature.valid(BODY, "garbage", SECRET, NOW));
        assertFalse(StripeSignature.valid(BODY, "t=abc,v1=00", SECRET, NOW));
        assertFalse(StripeSignature.valid(BODY, header(NOW.getEpochSecond(), BODY, SECRET), "", NOW));
    }
}
