package com.resumepipeline.github;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class GithubAppAuthTest {

    private static final KeyPair KEYS = generate();

    private static KeyPair generate() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String pem(String label, byte[] der) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END " + label + "-----\n";
    }

    /** PKCS#8 for a 2048-bit RSA key is a 26-byte header wrapped around the PKCS#1 body. */
    private static byte[] pkcs1() {
        byte[] p8 = KEYS.getPrivate().getEncoded();
        return Arrays.copyOfRange(p8, 26, p8.length);
    }

    private static GithubAppAuth auth(String pem) {
        return new GithubAppAuth("12345", "anvilcv", "Iv1.client", "secret", pem);
    }

    @Test
    void signsVerifiableJwtWithPkcs8Key() throws Exception {
        assertJwtValid(auth(pem("PRIVATE KEY", KEYS.getPrivate().getEncoded())).appJwt());
    }

    @Test
    void acceptsPkcs1KeyAsGithubDownloadsIt() throws Exception {
        assertJwtValid(auth(pem("RSA PRIVATE KEY", pkcs1())).appJwt());
    }

    @Test
    void acceptsSingleLinePemWithEscapedNewlines() throws Exception {
        String oneLine = pem("RSA PRIVATE KEY", pkcs1()).replace("\n", "\\n");
        assertJwtValid(auth(oneLine).appJwt());
    }

    @Test
    void unconfiguredAppRefusesToSign() {
        GithubAppAuth none = new GithubAppAuth("", "", "", "", "");
        assertFalse(none.isConfigured());
        assertThrows(GithubException.NotConfigured.class, none::appJwt);
    }

    @Test
    void garbageKeyFailsFastAtStartup() {
        assertThrows(IllegalStateException.class, () -> auth("-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----"));
    }

    private static void assertJwtValid(String jwt) throws Exception {
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length);
        Signature v = Signature.getInstance("SHA256withRSA");
        v.initVerify(KEYS.getPublic());
        v.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
        assertTrue(v.verify(Base64.getUrlDecoder().decode(parts[2])));
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertTrue(payload.contains("\"iss\":\"12345\""));
    }
}
