package com.resumepipeline.llm.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Reads and writes the singleton {@code llm_settings} row, merged over application.yml.
 *
 * The merge rule is one line: a blank or null DB column falls back to yml. That is what
 * lets this land on a running deploy with no action, and what keeps env as a backstop
 * if a bad save in the admin UI would otherwise leave the pipeline with no credentials.
 */
@Service
public class LlmSettingsService {

    private static final Logger log = LoggerFactory.getLogger(LlmSettingsService.class);

    private final LlmSettingsRepository repo;
    private final SecretCipher cipher;

    // --- application.yml fallbacks ---
    private final String ymlProvider;
    private final String ymlGeminiKey, ymlGeminiGenerate, ymlGeminiMatch, ymlGeminiCleanJd;
    private final String ymlOpencodeKey, ymlOpencodeBaseUrl, ymlOpencodeGenerate, ymlOpencodeMatch, ymlOpencodeCleanJd;
    private final String ymlOpenaiKey, ymlOpenaiBaseUrl, ymlOpenaiGenerate, ymlOpenaiMatch, ymlOpenaiCleanJd;

    public LlmSettingsService(
            LlmSettingsRepository repo,
            SecretCipher cipher,
            @Value("${llm.provider:gemini}") String ymlProvider,
            @Value("${llm.gemini.api-key:}") String ymlGeminiKey,
            @Value("${llm.gemini.model.generate:}") String ymlGeminiGenerate,
            @Value("${llm.gemini.model.match:}") String ymlGeminiMatch,
            @Value("${llm.gemini.model.clean-jd:}") String ymlGeminiCleanJd,
            @Value("${llm.opencode.api-key:}") String ymlOpencodeKey,
            @Value("${llm.opencode.base-url:https://opencode.ai/zen/v1}") String ymlOpencodeBaseUrl,
            @Value("${llm.opencode.model.generate:}") String ymlOpencodeGenerate,
            @Value("${llm.opencode.model.match:}") String ymlOpencodeMatch,
            @Value("${llm.opencode.model.clean-jd:}") String ymlOpencodeCleanJd,
            @Value("${llm.openai.api-key:}") String ymlOpenaiKey,
            @Value("${llm.openai.base-url:https://api.openai.com/v1}") String ymlOpenaiBaseUrl,
            @Value("${llm.openai.model.generate:}") String ymlOpenaiGenerate,
            @Value("${llm.openai.model.match:}") String ymlOpenaiMatch,
            @Value("${llm.openai.model.clean-jd:}") String ymlOpenaiCleanJd) {
        this.repo = repo;
        this.cipher = cipher;
        this.ymlProvider = ymlProvider;
        this.ymlGeminiKey = ymlGeminiKey;
        this.ymlGeminiGenerate = ymlGeminiGenerate;
        this.ymlGeminiMatch = ymlGeminiMatch;
        this.ymlGeminiCleanJd = ymlGeminiCleanJd;
        this.ymlOpencodeKey = ymlOpencodeKey;
        this.ymlOpencodeBaseUrl = ymlOpencodeBaseUrl;
        this.ymlOpencodeGenerate = ymlOpencodeGenerate;
        this.ymlOpencodeMatch = ymlOpencodeMatch;
        this.ymlOpencodeCleanJd = ymlOpencodeCleanJd;
        this.ymlOpenaiKey = ymlOpenaiKey;
        this.ymlOpenaiBaseUrl = ymlOpenaiBaseUrl;
        this.ymlOpenaiGenerate = ymlOpenaiGenerate;
        this.ymlOpenaiMatch = ymlOpenaiMatch;
        this.ymlOpenaiCleanJd = ymlOpenaiCleanJd;
    }

    // --- types ---

    /** Effective, decrypted config for one provider: everything a client needs to be built. */
    public record Resolved(String provider, String apiKey, String baseUrl,
                           String generateModel, String matchModel, String cleanJdModel) {}

    /** Resolved active provider plus the row version the router caches against. */
    public record Snapshot(Instant version, Resolved active) {}

    /** What the admin UI sees: models and base URLs in the clear, keys masked. */
    public record View(String provider, boolean secretKeyConfigured, Instant updatedAt, String updatedBy,
                       ProviderView gemini, ProviderView opencode, ProviderView openai) {}

    public record ProviderView(String apiKeyMasked, boolean apiKeyFromDb, String baseUrl,
                               String generateModel, String matchModel, String cleanJdModel) {}

    /** PUT body. A null or blank {@code apiKey} means "keep the stored one", never "clear it". */
    public record UpdateRequest(String provider,
                                ProviderUpdate gemini, ProviderUpdate opencode, ProviderUpdate openai) {}

    public record ProviderUpdate(String apiKey, String baseUrl,
                                 String generateModel, String matchModel, String cleanJdModel) {}

    // --- reads ---

    @Transactional
    public LlmSettings row() {
        return repo.findFirstByOrderByUpdatedAtAsc().orElseGet(() -> {
            // V20 seeds this row; only reachable if someone deleted it by hand.
            LlmSettings s = new LlmSettings();
            s.setId(UUID.randomUUID());
            s.setUpdatedAt(Instant.now());
            return repo.save(s);
        });
    }

    @Transactional
    public Snapshot current() {
        LlmSettings s = row();
        return new Snapshot(s.getUpdatedAt(), resolve(activeProvider(s), s));
    }

    public String activeProvider(LlmSettings s) {
        return blank(s.getProvider()) ? ymlProvider : s.getProvider();
    }

    /** Effective config for {@code provider}: DB over yml, key decrypted. */
    public Resolved resolve(String provider, LlmSettings s) {
        return switch (provider == null ? "" : provider.toLowerCase()) {
            case "opencode" -> new Resolved("opencode",
                    key(s.getOpencodeApiKeyEnc(), ymlOpencodeKey),
                    or(s.getOpencodeBaseUrl(), ymlOpencodeBaseUrl),
                    or(s.getOpencodeModelGenerate(), ymlOpencodeGenerate),
                    or(s.getOpencodeModelMatch(), ymlOpencodeMatch),
                    or(s.getOpencodeModelCleanJd(), ymlOpencodeCleanJd));
            case "openai" -> new Resolved("openai",
                    key(s.getOpenaiApiKeyEnc(), ymlOpenaiKey),
                    or(s.getOpenaiBaseUrl(), ymlOpenaiBaseUrl),
                    or(s.getOpenaiModelGenerate(), ymlOpenaiGenerate),
                    or(s.getOpenaiModelMatch(), ymlOpenaiMatch),
                    or(s.getOpenaiModelCleanJd(), ymlOpenaiCleanJd));
            // Gemini is the default, so an unrecognised value degrades to a working
            // provider instead of failing the whole pipeline at call time.
            default -> new Resolved("gemini",
                    key(s.getGeminiApiKeyEnc(), ymlGeminiKey),
                    null,
                    or(s.getGeminiModelGenerate(), ymlGeminiGenerate),
                    or(s.getGeminiModelMatch(), ymlGeminiMatch),
                    or(s.getGeminiModelCleanJd(), ymlGeminiCleanJd));
        };
    }

    @Transactional
    public View view() {
        LlmSettings s = row();
        return new View(
                activeProvider(s),
                cipher.isConfigured(),
                s.getUpdatedAt(),
                s.getUpdatedBy(),
                new ProviderView(mask(s.getGeminiApiKeyEnc(), ymlGeminiKey), !blank(s.getGeminiApiKeyEnc()), null,
                        or(s.getGeminiModelGenerate(), ymlGeminiGenerate),
                        or(s.getGeminiModelMatch(), ymlGeminiMatch),
                        or(s.getGeminiModelCleanJd(), ymlGeminiCleanJd)),
                new ProviderView(mask(s.getOpencodeApiKeyEnc(), ymlOpencodeKey), !blank(s.getOpencodeApiKeyEnc()),
                        or(s.getOpencodeBaseUrl(), ymlOpencodeBaseUrl),
                        or(s.getOpencodeModelGenerate(), ymlOpencodeGenerate),
                        or(s.getOpencodeModelMatch(), ymlOpencodeMatch),
                        or(s.getOpencodeModelCleanJd(), ymlOpencodeCleanJd)),
                new ProviderView(mask(s.getOpenaiApiKeyEnc(), ymlOpenaiKey), !blank(s.getOpenaiApiKeyEnc()),
                        or(s.getOpenaiBaseUrl(), ymlOpenaiBaseUrl),
                        or(s.getOpenaiModelGenerate(), ymlOpenaiGenerate),
                        or(s.getOpenaiModelMatch(), ymlOpenaiMatch),
                        or(s.getOpenaiModelCleanJd(), ymlOpenaiCleanJd)));
    }

    // --- writes ---

    @Transactional
    public View update(UpdateRequest req, String actingUsername) {
        LlmSettings s = row();

        if (!blank(req.provider())) s.setProvider(req.provider().toLowerCase());

        if (req.gemini() != null) {
            ProviderUpdate g = req.gemini();
            if (!blank(g.apiKey())) s.setGeminiApiKeyEnc(cipher.encrypt(g.apiKey().trim()));
            s.setGeminiModelGenerate(trimToNull(g.generateModel()));
            s.setGeminiModelMatch(trimToNull(g.matchModel()));
            s.setGeminiModelCleanJd(trimToNull(g.cleanJdModel()));
        }
        if (req.opencode() != null) {
            ProviderUpdate o = req.opencode();
            if (!blank(o.apiKey())) s.setOpencodeApiKeyEnc(cipher.encrypt(o.apiKey().trim()));
            s.setOpencodeBaseUrl(trimToNull(o.baseUrl()));
            s.setOpencodeModelGenerate(trimToNull(o.generateModel()));
            s.setOpencodeModelMatch(trimToNull(o.matchModel()));
            s.setOpencodeModelCleanJd(trimToNull(o.cleanJdModel()));
        }
        if (req.openai() != null) {
            ProviderUpdate o = req.openai();
            if (!blank(o.apiKey())) s.setOpenaiApiKeyEnc(cipher.encrypt(o.apiKey().trim()));
            s.setOpenaiBaseUrl(trimToNull(o.baseUrl()));
            s.setOpenaiModelGenerate(trimToNull(o.generateModel()));
            s.setOpenaiModelMatch(trimToNull(o.matchModel()));
            s.setOpenaiModelCleanJd(trimToNull(o.cleanJdModel()));
        }

        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy(actingUsername);
        repo.save(s);
        log.info("llm_settings updated by {} - active provider now {}", actingUsername, activeProvider(s));
        return view();
    }

    // --- helpers ---

    /**
     * Decrypts the stored key, falling back to yml. A stored key with no cipher key
     * configured logs and falls back rather than throwing, so the pipeline keeps running
     * on the env credential instead of every LLM call dying.
     */
    private String key(String storedEnc, String ymlValue) {
        if (blank(storedEnc)) return ymlValue;
        if (!cipher.isConfigured()) {
            log.warn("llm_settings holds an encrypted API key but LLM_SECRET_KEY is unset - falling back to env.");
            return ymlValue;
        }
        try {
            return cipher.decrypt(storedEnc);
        } catch (RuntimeException e) {
            log.error("Failed to decrypt stored LLM API key - falling back to env. {}", e.getMessage());
            return ymlValue;
        }
    }

    /** Shows the last 4 characters only: enough to tell two keys apart, not enough to use one. */
    private String mask(String storedEnc, String ymlValue) {
        String plain;
        if (!blank(storedEnc)) {
            if (!cipher.isConfigured()) return "(encrypted - LLM_SECRET_KEY unset)";
            try {
                plain = cipher.decrypt(storedEnc);
            } catch (RuntimeException e) {
                return "(undecryptable - wrong LLM_SECRET_KEY?)";
            }
        } else {
            plain = ymlValue;
        }
        if (blank(plain)) return null;
        String tail = plain.length() <= 4 ? plain : plain.substring(plain.length() - 4);
        return "..." + tail;
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String or(String a, String b) { return blank(a) ? b : a; }
    private static String trimToNull(String s) { return blank(s) ? null : s.trim(); }
}
