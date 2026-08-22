package com.resumepipeline.llm.settings;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * The single {@code llm_settings} row. Every provider field is nullable and null means
 * "use the application.yml/env value" — see {@link LlmSettingsService} for the merge.
 */
@Entity
@Table(name = "llm_settings")
public class LlmSettings {

    @Id
    private UUID id;

    @Column(nullable = false)
    private boolean singleton = true;

    private String provider;

    @Column(name = "gemini_api_key_enc")    private String geminiApiKeyEnc;
    @Column(name = "gemini_model_generate") private String geminiModelGenerate;
    @Column(name = "gemini_model_match")    private String geminiModelMatch;
    @Column(name = "gemini_model_clean_jd") private String geminiModelCleanJd;

    @Column(name = "opencode_api_key_enc")    private String opencodeApiKeyEnc;
    @Column(name = "opencode_base_url")       private String opencodeBaseUrl;
    @Column(name = "opencode_model_generate") private String opencodeModelGenerate;
    @Column(name = "opencode_model_match")    private String opencodeModelMatch;
    @Column(name = "opencode_model_clean_jd") private String opencodeModelCleanJd;

    @Column(name = "openai_api_key_enc")    private String openaiApiKeyEnc;
    @Column(name = "openai_base_url")       private String openaiBaseUrl;
    @Column(name = "openai_model_generate") private String openaiModelGenerate;
    @Column(name = "openai_model_match")    private String openaiModelMatch;
    @Column(name = "openai_model_clean_jd") private String openaiModelCleanJd;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by")
    private String updatedBy;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public boolean isSingleton() { return singleton; }
    public void setSingleton(boolean v) { this.singleton = v; }

    public String getProvider() { return provider; }
    public void setProvider(String v) { this.provider = v; }

    public String getGeminiApiKeyEnc() { return geminiApiKeyEnc; }
    public void setGeminiApiKeyEnc(String v) { this.geminiApiKeyEnc = v; }
    public String getGeminiModelGenerate() { return geminiModelGenerate; }
    public void setGeminiModelGenerate(String v) { this.geminiModelGenerate = v; }
    public String getGeminiModelMatch() { return geminiModelMatch; }
    public void setGeminiModelMatch(String v) { this.geminiModelMatch = v; }
    public String getGeminiModelCleanJd() { return geminiModelCleanJd; }
    public void setGeminiModelCleanJd(String v) { this.geminiModelCleanJd = v; }

    public String getOpencodeApiKeyEnc() { return opencodeApiKeyEnc; }
    public void setOpencodeApiKeyEnc(String v) { this.opencodeApiKeyEnc = v; }
    public String getOpencodeBaseUrl() { return opencodeBaseUrl; }
    public void setOpencodeBaseUrl(String v) { this.opencodeBaseUrl = v; }
    public String getOpencodeModelGenerate() { return opencodeModelGenerate; }
    public void setOpencodeModelGenerate(String v) { this.opencodeModelGenerate = v; }
    public String getOpencodeModelMatch() { return opencodeModelMatch; }
    public void setOpencodeModelMatch(String v) { this.opencodeModelMatch = v; }
    public String getOpencodeModelCleanJd() { return opencodeModelCleanJd; }
    public void setOpencodeModelCleanJd(String v) { this.opencodeModelCleanJd = v; }

    public String getOpenaiApiKeyEnc() { return openaiApiKeyEnc; }
    public void setOpenaiApiKeyEnc(String v) { this.openaiApiKeyEnc = v; }
    public String getOpenaiBaseUrl() { return openaiBaseUrl; }
    public void setOpenaiBaseUrl(String v) { this.openaiBaseUrl = v; }
    public String getOpenaiModelGenerate() { return openaiModelGenerate; }
    public void setOpenaiModelGenerate(String v) { this.openaiModelGenerate = v; }
    public String getOpenaiModelMatch() { return openaiModelMatch; }
    public void setOpenaiModelMatch(String v) { this.openaiModelMatch = v; }
    public String getOpenaiModelCleanJd() { return openaiModelCleanJd; }
    public void setOpenaiModelCleanJd(String v) { this.openaiModelCleanJd = v; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String v) { this.updatedBy = v; }
}
