package com.resumepipeline.llm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Category "lenses" that focus bullet generation on a specific audience-targeted angle
 * of the same role/project. Each lens prepends a short paragraph to the prompt telling
 * the LLM what to emphasize and what to call out by name.
 */
public final class CategoryLenses {

    public static final Map<String, String> LENSES = new LinkedHashMap<>();
    static {
        LENSES.put("ai-ml", """
                LENS: AI / Machine Learning.
                Lead with: production RAG pipelines, hybrid full-text + vector search, embedding-similarity
                models, multi-stage retrieval (cheatsheet -> semantic cache -> tool exec), multi-tool LLM
                agents with tool-loop budgets, provider failover, prompt design, semantic caches. Name the
                models, frameworks, vector stores and providers THE SOURCE NAMES — never one it does not.
                Quantify with latency (ms), call-volume reduction (%), and accuracy where the source supports it.
                Avoid generic "used AI to do X" bullets — name the technique.""");

        LENSES.put("backend", """
                LENS: Backend & Data Architecture.
                Lead with: API design at scale (mention endpoint count if material), data-modeling decisions
                (relational vs document vs hybrid), index choices (2dsphere, GIN, partial, composite),
                migration work that fixed real corruption, integration patterns (idempotency, signature
                verification), versioned migrations as the schema source of truth. Name the databases,
                ORMs and migration tools THE SOURCE NAMES. Quantify with row counts, endpoint counts,
                migration time, and per-tenant boundary enforcement.""");

        LENSES.put("frontend", """
                LENS: Frontend & Product.
                Lead with: design-token / shared-component systems with class-based dark mode, responsive
                layouts with desktop/mobile parity, complex client-side state (15+ filter dimensions, URL
                serialization for shareable searches), interactive visualizations (D3 hierarchical trees,
                map rendering), accessibility, mobile-first patterns. Name the frameworks and versions
                THE SOURCE NAMES, and no others. Quantify with re-render reductions, filter
                dimensions, and dataset sizes rendered.""");

        LENSES.put("data", """
                LENS: Data Engineering.
                Lead with: ingestion pipelines (scrape-and-parse, ETL), parser design
                (AST-based, recursive tokenization, cycle detection), data quality / validation against
                authoritative sources, analytics queries at scale, geospatial joins, search index
                management. Quote data volumes verbatim (file size in MB, row counts, course counts).
                Frame each bullet as: what was ingested -> with what technique -> at what scale -> with
                what curation/automation outcome.""");

        LENSES.put("security", """
                LENS: Security & Authentication.
                Lead with: RBAC schemes with database-enforced constraints, encryption-at-rest with
                specific cipher names (AES-256-GCM), signed-and-idempotent webhooks with replay protection,
                multi-tenant isolation enforced at a single authorization boundary (not duplicated per
                route), and any compliance framework THE SOURCE NAMES. Use adversarial-thinking framing:
                what threat does this defend against? Name the auth libraries and providers THE SOURCE
                NAMES, and no others. Avoid generic "added authentication" bullets.""");

        LENSES.put("devops", """
                LENS: Infrastructure & DevOps.
                Lead with: CI/CD pipeline structure (parallel unit + end-to-end suites, environment-isolated
                dev/prod), monorepo ownership at scale (mention contributor count + commit count if
                impressive), GitOps deploys, VPS deploy automation with failure-alert email, centralized
                third-party config so vendor rotations are a single-line change. Quantify with build counts,
                contributor counts, broken-build rates ("zero broken main builds to date").""");

        LENSES.put("systems", """
                LENS: Distributed Systems & Real-time.
                Lead with: async fan-out with thread-safe primitives, message bus patterns, idempotent
                webhook handling under provider retries, WebSocket delta processing, per-vendor rate-limit
                handling, decoupling latency-critical user paths from background automations via webhook
                fan-out, hard timeout caps preventing runaway tool loops. Quantify with concurrent event
                counts, per-vendor metrics, and latency budgets protected.""");

        LENSES.put("comms", """
                LENS: Real-time Communications.
                Lead with: provider-agnostic stacks across telephony / SMS / email vendors unified behind a
                single webhook layer, WebRTC dialing with live SMS feeds over a realtime channel,
                machine-voicemail detection with SIP bridging, transcription
                routing into downstream automation, regulatory compliance at the message boundary
                (STOP/opt-out, after-hours rules). Quantify with vendor counts and unified-timeline coverage.""");
    }

    /**
     * Returns the lens block for the given category, or null if no lens applies
     * (e.g. category = "general" or unknown).
     */
    public static String lensFor(String category) {
        if (category == null || category.isBlank()) return null;
        return LENSES.get(category);
    }

    private CategoryLenses() {}
}
