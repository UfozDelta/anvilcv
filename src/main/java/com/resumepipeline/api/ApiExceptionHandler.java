package com.resumepipeline.api;

import com.google.genai.errors.ApiException;
import com.google.genai.errors.ClientException;
import com.google.genai.errors.ServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

/**
 * Surfaces upstream LLM errors, validation failures, etc. as structured JSON so
 * the frontend can show a real message instead of a generic 500.
 */
@ControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ClientException.class)
    public ResponseEntity<Map<String, Object>> handleLlmClient(ClientException e) {
        log.warn("LLM client error: {}", e.getMessage());
        HttpStatus status = mapLlmStatus(e);
        return ResponseEntity.status(status).body(Map.of(
                "source", "llm",
                "status", status.value(),
                "message", trimLlmMessage(e.getMessage()),
                "hint", hintForStatus(status)
        ));
    }

    @ExceptionHandler(ServerException.class)
    public ResponseEntity<Map<String, Object>> handleLlmServer(ServerException e) {
        log.warn("LLM server error: {}", e.getMessage());
        return ResponseEntity.status(502).body(Map.of(
                "source", "llm",
                "status", 502,
                "message", trimLlmMessage(e.getMessage()),
                "hint", "LLM is having a bad time. Try again in a moment."
        ));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleLlmOther(ApiException e) {
        log.warn("LLM API error: {}", e.getMessage());
        return ResponseEntity.status(502).body(Map.of(
                "source", "llm",
                "status", 502,
                "message", trimLlmMessage(e.getMessage())
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadInput(IllegalArgumentException e) {
        return ResponseEntity.status(400).body(Map.of(
                "source", "validation",
                "status", 400,
                "message", e.getMessage() == null ? "Bad request" : e.getMessage()
        ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleState(IllegalStateException e) {
        return ResponseEntity.status(409).body(Map.of(
                "source", "state",
                "status", 409,
                "message", e.getMessage() == null ? "Conflict" : e.getMessage()
        ));
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(org.springframework.web.server.ResponseStatusException e) {
        // Preserve the intended HTTP status (e.g. 404 from service ownership checks).
        // Without this, the broad RuntimeException handler below would turn it into a 500.
        return ResponseEntity.status(e.getStatusCode()).body(Map.of(
                "source", "request",
                "status", e.getStatusCode().value(),
                "message", e.getReason() == null ? "Request failed" : e.getReason()
        ));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException e) {
        // Unwrap LLM errors that escaped via parsing failures.
        Throwable cause = e.getCause();
        if (cause instanceof ClientException ce)  return handleLlmClient(ce);
        if (cause instanceof ServerException se)  return handleLlmServer(se);
        if (cause instanceof ApiException ae)     return handleLlmOther(ae);

        log.error("Unhandled runtime error", e);
        return ResponseEntity.status(500).body(Map.of(
                "source", "server",
                "status", 500,
                "message", e.getMessage() == null ? "Internal error" : e.getMessage()
        ));
    }

    private static HttpStatus mapLlmStatus(ClientException e) {
        // SDK exception messages start with the HTTP status code, e.g. "429 ..."
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.startsWith("429")) return HttpStatus.TOO_MANY_REQUESTS;
        if (msg.startsWith("401") || msg.startsWith("403")) return HttpStatus.UNAUTHORIZED;
        if (msg.startsWith("400")) return HttpStatus.BAD_REQUEST;
        if (msg.startsWith("404")) return HttpStatus.NOT_FOUND;
        return HttpStatus.BAD_GATEWAY;
    }

    /** LLM error messages are giant URL-laden walls of text — keep the useful bit. */
    private static String trimLlmMessage(String raw) {
        if (raw == null) return "LLM error";
        String s = raw.replaceAll("\\s+", " ").trim();
        int firstLink = s.indexOf("https://");
        if (firstLink > 60) s = s.substring(0, firstLink).trim();
        int firstAsterisk = s.indexOf(" *");
        if (firstAsterisk > 0 && firstAsterisk < s.length() - 1) {
            String head = s.substring(0, firstAsterisk).trim();
            if (head.length() > 30) s = head;
        }
        if (s.length() > 400) s = s.substring(0, 400) + "…";
        return s;
    }

    private static String hintForStatus(HttpStatus s) {
        return switch (s) {
            case TOO_MANY_REQUESTS -> "LLM free-tier rate limit hit. Wait ~30s and retry, or enable billing.";
            case UNAUTHORIZED -> "Check LLM_API_KEY in application-local.yml.";
            case BAD_REQUEST -> "LLM rejected the request payload.";
            default -> "";
        };
    }
}
