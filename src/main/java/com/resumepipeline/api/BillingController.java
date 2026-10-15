package com.resumepipeline.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumepipeline.auth.AuthUtils;
import com.resumepipeline.billing.BillingService;
import com.resumepipeline.billing.StripeWebhookVerifier;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/** Starts a Stripe Checkout or portal session, and receives Stripe's signed webhook. */
@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record CheckoutRequest(@NotBlank String plan) {}
    public record UrlResponse(String url) {}

    private final BillingService billing;
    private final StripeWebhookVerifier verifier;

    public BillingController(BillingService billing, StripeWebhookVerifier verifier) {
        this.billing = billing;
        this.verifier = verifier;
    }

    @PostMapping("/checkout")
    public UrlResponse checkout(@RequestBody CheckoutRequest req, Authentication auth) {
        return new UrlResponse(billing.checkout(AuthUtils.userId(auth), req.plan()));
    }

    @PostMapping("/portal")
    public UrlResponse portal(Authentication auth) {
        return new UrlResponse(billing.portal(AuthUtils.userId(auth)));
    }

    /** Public route: the signature is the credential. The body stays raw bytes for the check. */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody byte[] body,
                                        @RequestHeader(value = "Stripe-Signature", required = false) String signature)
            throws IOException {
        if (!verifier.enabled()) return ResponseEntity.status(503).build();
        if (!verifier.valid(body, signature)) return ResponseEntity.status(401).build();
        JsonNode event = MAPPER.readTree(body);
        billing.handle(event);
        return ResponseEntity.noContent().build();
    }
}
