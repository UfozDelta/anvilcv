package com.resumepipeline.billing;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Supplier;

/** Stripe's REST API, form-encoded, authenticated with the secret key. */
@Component
public class StripeHttpApi implements StripeApi {

    private static final Logger log = LoggerFactory.getLogger(StripeHttpApi.class);

    private final RestClient rest;
    private final boolean configured;

    public StripeHttpApi(RestClient.Builder builder, @Value("${stripe.secret-key:}") String secretKey) {
        this.configured = !secretKey.isBlank();
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(5_000);
        timeouts.setReadTimeout(15_000);
        this.rest = builder
                .requestFactory(timeouts)
                .baseUrl("https://api.stripe.com/v1")
                .defaultHeaders(h -> { if (!secretKey.isBlank()) h.setBearerAuth(secretKey); })
                .build();
    }

    @Override
    public String checkoutUrl(CheckoutRequest r) {
        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        f.add("mode", r.mode());
        f.add("line_items[0][price]", r.priceId());
        f.add("line_items[0][quantity]", "1");
        f.add("success_url", r.successUrl());
        f.add("cancel_url", r.cancelUrl());
        f.add("client_reference_id", r.userId().toString());
        f.add("metadata[plan]", r.plan());
        f.add("metadata[user_id]", r.userId().toString());
        if (r.customerId() != null) f.add("customer", r.customerId());
        else {
            f.add("customer_email", r.email());
            // A one-off payment makes no customer unless asked; the portal and later events need one.
            if ("payment".equals(r.mode())) f.add("customer_creation", "always");
        }
        if ("subscription".equals(r.mode())) f.add("subscription_data[metadata][plan]", r.plan());
        return post("/checkout/sessions", f);
    }

    @Override
    public String portalUrl(String customerId, String returnUrl) {
        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        f.add("customer", customerId);
        f.add("return_url", returnUrl);
        return post("/billing_portal/sessions", f);
    }

    @Override
    public void cancelSubscription(String subscriptionId) {
        call(() -> rest.delete().uri("/subscriptions/{id}", subscriptionId).retrieve().toBodilessEntity());
    }

    private String post(String path, MultiValueMap<String, String> form) {
        JsonNode res = call(() -> rest.post().uri(path)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class));
        String url = res == null ? "" : res.path("url").asText();
        if (url.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Billing is unavailable. Try again.");
        return url;
    }

    /** Stripe error text can name keys and accounts: log it here, show the user a fixed message. */
    private <T> T call(Supplier<T> request) {
        if (!configured) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Billing is not set up.");
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            log.error("STRIPE_API status={} body={}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Billing is unavailable. Try again.");
        }
    }
}
