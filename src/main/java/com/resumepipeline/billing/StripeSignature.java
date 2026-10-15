package com.resumepipeline.billing;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Checks the {@code Stripe-Signature} header: {@code t=<unix>,v1=<hmac-sha256 of "t.body">}. */
final class StripeSignature {

    static final long TOLERANCE_SECONDS = 300;

    private StripeSignature() {}

    static boolean valid(byte[] body, String header, String secret, Instant now) {
        if (header == null || secret == null || secret.isBlank()) return false;
        String t = null;
        List<String> v1 = new ArrayList<>();
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            if (kv[0].equals("t")) t = kv[1];
            else if (kv[0].equals("v1")) v1.add(kv[1]);
        }
        if (t == null || v1.isEmpty()) return false;
        long ts;
        try { ts = Long.parseLong(t); } catch (NumberFormatException e) { return false; }
        if (Math.abs(now.getEpochSecond() - ts) > TOLERANCE_SECONDS) return false;

        byte[] expected = hmac(secret, t, body);
        for (String candidate : v1) {
            byte[] got;
            try { got = HexFormat.of().parseHex(candidate); } catch (IllegalArgumentException e) { continue; }
            if (MessageDigest.isEqual(expected, got)) return true;
        }
        return false;
    }

    static byte[] hmac(String secret, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(body);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
