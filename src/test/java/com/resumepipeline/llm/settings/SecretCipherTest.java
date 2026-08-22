package com.resumepipeline.llm.settings;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class SecretCipherTest {

    private static String randomKey() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }

    @Test
    void roundTrips() {
        SecretCipher cipher = new SecretCipher(randomKey());
        String secret = "sk-proj-abcdef0123456789";

        String enc = cipher.encrypt(secret);
        assertNotEquals(secret, enc, "ciphertext must not be the plaintext");
        assertEquals(secret, cipher.decrypt(enc));
    }

    @Test
    void sameValueEncryptsDifferentlyEachTime() {
        // Fresh IV per call — identical keys must not produce identical ciphertext,
        // otherwise the column leaks which providers share a key.
        SecretCipher cipher = new SecretCipher(randomKey());
        assertNotEquals(cipher.encrypt("same"), cipher.encrypt("same"));
    }

    @Test
    void wrongKeyFailsInsteadOfReturningGarbage() {
        String enc = new SecretCipher(randomKey()).encrypt("sk-secret");
        SecretCipher other = new SecretCipher(randomKey());

        assertThrows(SecretCipher.GeneralSecurityRuntimeException.class, () -> other.decrypt(enc));
    }

    @Test
    void refusesToHandleSecretsWithoutAKey() {
        SecretCipher unconfigured = new SecretCipher("");

        assertFalse(unconfigured.isConfigured());
        assertThrows(SecretCipher.GeneralSecurityRuntimeException.class, () -> unconfigured.encrypt("sk-secret"));
    }

    @Test
    void rejectsWrongLengthKeyAtConstruction() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class, () -> new SecretCipher(tooShort));
    }
}
