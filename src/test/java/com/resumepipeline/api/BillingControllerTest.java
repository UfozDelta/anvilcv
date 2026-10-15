package com.resumepipeline.api;

import com.resumepipeline.auth.AppUserDetailsService;
import com.resumepipeline.auth.SecurityConfig;
import com.resumepipeline.billing.BillingService;
import com.resumepipeline.billing.StripeWebhookVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static com.resumepipeline.api.WebTestSecurity.user;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The webhook is public and signature-gated; checkout and the portal need a login. */
@WebMvcTest(BillingController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class, StripeWebhookVerifier.class})
@TestPropertySource(properties = {"cors.allowed-origin=http://localhost:5173", "stripe.webhook-secret=whsec_test"})
class BillingControllerTest {

    static final String SECRET = "whsec_test";
    static final String BODY = "{\"id\":\"evt_1\",\"type\":\"customer.subscription.deleted\",\"data\":{\"object\":{\"id\":\"sub_1\"}}}";

    @Autowired MockMvc mvc;
    @MockitoBean BillingService billing;
    @MockitoBean AppUserDetailsService userDetailsService;

    private static String sign(String body, long t) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((t + ".").getBytes(StandardCharsets.UTF_8));
        return "t=" + t + ",v1=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void aSignedWebhookIsAcceptedWithoutLoginAndHandled() throws Exception {
        mvc.perform(post("/api/billing/webhook").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("Stripe-Signature", sign(BODY, Instant.now().getEpochSecond())))
                .andExpect(status().isNoContent());
        verify(billing).handle(any());
    }

    @Test
    void anUnsignedOrForgedWebhookIs401AndChangesNothing() throws Exception {
        mvc.perform(post("/api/billing/webhook").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/billing/webhook").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("Stripe-Signature", "t=" + Instant.now().getEpochSecond() + ",v1=" + "ab".repeat(32)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(billing);
    }

    @Test
    void aValidSignatureOnADifferentBodyIsRejected() throws Exception {
        String sig = sign(BODY, Instant.now().getEpochSecond());
        mvc.perform(post("/api/billing/webhook").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("sub_1", "sub_2")).header("Stripe-Signature", sig))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(billing);
    }

    @Test
    void aReplayedOldSignatureIsRejected() throws Exception {
        mvc.perform(post("/api/billing/webhook").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("Stripe-Signature", sign(BODY, Instant.now().getEpochSecond() - 3600)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(billing);
    }

    @Test
    void checkoutAndPortalNeedALogin() throws Exception {
        mvc.perform(post("/api/billing/checkout").contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"pro\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/billing/portal"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(billing);
    }

    @Test
    void checkoutReturnsTheStripeUrlForTheSignedInUser() throws Exception {
        UUID uid = UUID.randomUUID();
        when(billing.checkout(uid, "pro")).thenReturn("https://stripe.test/c");
        mvc.perform(post("/api/billing/checkout").with(user(uid)).contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"pro\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://stripe.test/c"));
    }

    @Test
    void checkoutWithAnUnknownPlanIs400() throws Exception {
        UUID uid = UUID.randomUUID();
        when(billing.checkout(uid, "gold")).thenThrow(new IllegalArgumentException("Unknown plan: gold"));
        mvc.perform(post("/api/billing/checkout").with(user(uid)).contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"gold\"}"))
                .andExpect(status().isBadRequest());
    }
}
