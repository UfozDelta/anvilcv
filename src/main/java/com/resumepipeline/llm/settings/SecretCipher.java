package com.resumepipeline.llm.settings;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM for the provider API keys stored in {@code llm_settings}.
 *
 * The keys have to be encrypted at rest: a Neon dump or one SQL-injection read would
 * otherwise hand over every provider credential in plaintext. The cipher key cannot
 * live in the database it protects, so it stays in the environment as
 * {@code LLM_SECRET_KEY} — base64, 32 bytes. Net effect is N provider keys collapsing
 * into 1 env var, not zero; zero is not reachable.
 *
 * With no key configured the app still boots and reads settings, but refuses to save
 * a secret rather than writing plaintext into a column named {@code _enc}.
 */
@Component
public class SecretCipher {

    private static final int IV_BYTES  = 12;   // GCM standard nonce length
    private static final int TAG_BITS  = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${llm.secret-key:}") String base64Key) {
        this.key = parseKey(base64Key);
    }

    private static SecretKey parseKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) return null;
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("LLM_SECRET_KEY is not valid base64", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException(
                    "LLM_SECRET_KEY must decode to 32 bytes (AES-256), got " + raw.length);
        }
        return new SecretKeySpec(raw, "AES");
    }

    /** True when a usable key is configured. The admin UI reads this to explain itself. */
    public boolean isConfigured() {
        return key != null;
    }

    /** Encrypts to base64(iv || ciphertext+tag). A fresh random IV per value — never reused. */
    public String encrypt(String plaintext) {
        requireKey();
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new GeneralSecurityRuntimeException("Failed to encrypt secret", e);
        }
    }

    /** Inverse of {@link #encrypt}. A wrong key fails the GCM tag check rather than returning garbage. */
    public String decrypt(String stored) {
        requireKey();
        if (stored == null || stored.isBlank()) return null;
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            if (all.length <= IV_BYTES) {
                throw new GeneralSecurityRuntimeException("Stored secret is too short to contain an IV", null);
            }
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(all, 0, iv, 0, IV_BYTES);

            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = c.doFinal(all, IV_BYTES, all.length - IV_BYTES);
            return new String(pt, java.nio.charset.StandardCharsets.UTF_8);
        } catch (GeneralSecurityRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new GeneralSecurityRuntimeException("Failed to decrypt secret — wrong LLM_SECRET_KEY?", e);
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new GeneralSecurityRuntimeException(
                    "LLM_SECRET_KEY is not set — refusing to handle provider API keys. "
                            + "Generate one with: openssl rand -base64 32", null);
        }
    }

    /** Unchecked so callers and Spring MVC handlers are not forced into checked-exception plumbing. */
    public static class GeneralSecurityRuntimeException extends RuntimeException {
        public GeneralSecurityRuntimeException(String message, Throwable cause) { super(message, cause); }
    }
}
