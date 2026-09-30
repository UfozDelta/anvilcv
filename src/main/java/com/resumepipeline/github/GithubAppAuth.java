package com.resumepipeline.github;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * GitHub App identity: the app id, its OAuth client pair, and the RS256 app JWT signed with
 * the app's private key. Everything the app can do on a user's repos flows from this key
 * (installation tokens are minted with the JWT), so it lives only in the environment.
 *
 * <p>With nothing configured the app still boots; {@link #isConfigured()} is false and the
 * GitHub endpoints answer 503 instead of the whole context failing to start.
 */
@Component
public class GithubAppAuth {

    private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();

    private final String appId;
    private final String slug;
    private final String clientId;
    private final String clientSecret;
    private final PrivateKey privateKey;

    public GithubAppAuth(@Value("${github.app.id:}") String appId,
                         @Value("${github.app.slug:}") String slug,
                         @Value("${github.app.client-id:}") String clientId,
                         @Value("${github.app.client-secret:}") String clientSecret,
                         @Value("${github.app.private-key:}") String privateKeyPem) {
        this.appId = appId;
        this.slug = slug;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.privateKey = privateKeyPem == null || privateKeyPem.isBlank() ? null : parsePem(privateKeyPem);
    }

    public boolean isConfigured() {
        return privateKey != null && !appId.isBlank() && !slug.isBlank()
                && !clientId.isBlank() && !clientSecret.isBlank();
    }

    public String appId()        { return appId; }
    public String slug()         { return slug; }
    public String clientId()     { return clientId; }
    public String clientSecret() { return clientSecret; }

    /** App JWT, valid 9 minutes (GitHub caps it at 10). iat is backdated 60s for clock skew. */
    public String appJwt() {
        if (!isConfigured()) throw new GithubException.NotConfigured();
        long now = System.currentTimeMillis() / 1000;
        String header = URL.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = URL.encodeToString(("{\"iat\":" + (now - 60) + ",\"exp\":" + (now + 540)
                + ",\"iss\":\"" + appId + "\"}").getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(privateKey);
            sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return signingInput + "." + URL.encodeToString(sig.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign GitHub App JWT", e);
        }
    }

    /**
     * Accepts the key GitHub hands out (PKCS#1, "BEGIN RSA PRIVATE KEY") or PKCS#8
     * ("BEGIN PRIVATE KEY"). Literal {@code \n} sequences are unescaped so the PEM can sit on
     * one line in an .env file.
     */
    static PrivateKey parsePem(String pem) {
        String text = pem.replace("\\n", "\n").trim();
        boolean pkcs1 = text.contains("BEGIN RSA PRIVATE KEY");
        String b64 = text.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(b64);
            if (pkcs1) der = wrapPkcs1(der);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("GITHUB_APP_PRIVATE_KEY is not a valid RSA PEM key", e);
        }
    }

    /** PKCS#1 RSAPrivateKey -> PKCS#8 PrivateKeyInfo, since the JDK KeyFactory only reads the latter. */
    private static byte[] wrapPkcs1(byte[] pkcs1) {
        byte[] version = {0x02, 0x01, 0x00};
        byte[] rsaAlgId = {0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
                (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(version);
        body.writeBytes(rsaAlgId);
        body.write(0x04);
        body.writeBytes(derLength(pkcs1.length));
        body.writeBytes(pkcs1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x30);
        out.writeBytes(derLength(body.size()));
        out.writeBytes(body.toByteArray());
        return out.toByteArray();
    }

    private static byte[] derLength(int n) {
        if (n < 128) return new byte[]{(byte) n};
        int bytes = n > 0xFFFF ? 3 : n > 0xFF ? 2 : 1;
        byte[] out = new byte[bytes + 1];
        out[0] = (byte) (0x80 | bytes);
        for (int i = bytes; i >= 1; i--) {
            out[i] = (byte) (n & 0xFF);
            n >>= 8;
        }
        return out;
    }
}
