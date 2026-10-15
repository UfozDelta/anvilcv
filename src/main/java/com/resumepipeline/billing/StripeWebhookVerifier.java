package com.resumepipeline.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** Holds the webhook secret and checks signatures against it. */
@Component
public class StripeWebhookVerifier {

    private final String secret;
    private final Clock clock = Clock.systemUTC();

    public StripeWebhookVerifier(@Value("${stripe.webhook-secret:}") String secret) {
        this.secret = secret;
    }

    public boolean enabled() { return !secret.isBlank(); }

    public boolean valid(byte[] body, String header) {
        return StripeSignature.valid(body, header, secret, clock.instant());
    }
}
