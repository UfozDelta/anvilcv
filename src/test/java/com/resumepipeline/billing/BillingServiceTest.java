package com.resumepipeline.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumepipeline.auth.AppUser;
import com.resumepipeline.auth.AppUserRepository;
import com.resumepipeline.quota.Plan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BillingServiceTest {

    static final ObjectMapper JSON = new ObjectMapper();
    static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    final UUID uid = UUID.randomUUID();
    AppUserRepository users = mock(AppUserRepository.class);
    StripeEventRepository events = mock(StripeEventRepository.class);
    StripeApi stripe = mock(StripeApi.class);
    BillingService billing = new BillingService(users, events, stripe, Clock.fixed(NOW, ZoneOffset.UTC), "https://app.test",
            Map.of(Plan.STARTER, "price_s", Plan.PRO, "price_p", Plan.PACK, "price_k"));
    AppUser user = new AppUser("u", "u@example.com", "x");

    @BeforeEach
    void setUp() {
        when(users.findById(uid)).thenReturn(Optional.of(user));
        when(events.claim(any(), any())).thenReturn(1);   // every event is new unless a test says otherwise
    }

    private static JsonNode event(String id, String type, String object) throws Exception {
        return JSON.readTree("{\"id\":\"" + id + "\",\"type\":\"" + type + "\",\"data\":{\"object\":" + object + "}}");
    }

    private String session(String mode, String plan, String extra) {
        return "{\"id\":\"cs_1\",\"mode\":\"" + mode + "\",\"client_reference_id\":\"" + uid + "\",\"customer\":\"cus_1\","
                + "\"metadata\":{\"plan\":\"" + plan + "\"}" + extra + "}";
    }

    // ---- checkout / portal ----

    @Test
    void checkoutSendsASubscriptionForStarterAndAPaymentForThePack() {
        when(stripe.checkoutUrl(any())).thenReturn("https://stripe.test/c");

        assertEquals("https://stripe.test/c", billing.checkout(uid, "starter"));
        billing.checkout(uid, "pack");

        var captor = org.mockito.ArgumentCaptor.forClass(StripeApi.CheckoutRequest.class);
        verify(stripe, times(2)).checkoutUrl(captor.capture());
        assertEquals("subscription", captor.getAllValues().get(0).mode());
        assertEquals("price_s", captor.getAllValues().get(0).priceId());
        assertEquals("payment", captor.getAllValues().get(1).mode());
        assertEquals("https://app.test/settings?billing=success", captor.getAllValues().get(0).successUrl());
    }

    @Test
    void checkoutRefusesFreeUnknownAndAlreadyPaidPlans() {
        assertThrows(IllegalArgumentException.class, () -> billing.checkout(uid, "free"));
        assertThrows(IllegalArgumentException.class, () -> billing.checkout(uid, "gold"));

        user.setPlan("pro");
        var e = assertThrows(ResponseStatusException.class, () -> billing.checkout(uid, "pack"));
        assertEquals(409, e.getStatusCode().value());
        verifyNoInteractions(stripe);
    }

    @Test
    void aUsedUpPackBuyerCanBuyAgain() {
        user.setPlan("pack");
        when(stripe.checkoutUrl(any())).thenReturn("https://stripe.test/c");
        assertEquals("https://stripe.test/c", billing.checkout(uid, "pack"));
        assertEquals("https://stripe.test/c", billing.checkout(uid, "pro"));
    }

    @Test
    void checkoutIs503WhenThePriceIsNotConfigured() {
        BillingService unset = new BillingService(users, events, stripe, Clock.systemUTC(), "https://app.test",
                Map.of(Plan.STARTER, "", Plan.PRO, "", Plan.PACK, ""));
        var e = assertThrows(ResponseStatusException.class, () -> unset.checkout(uid, "pro"));
        assertEquals(503, e.getStatusCode().value());
    }

    @Test
    void portalNeedsAStripeCustomer() {
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> billing.portal(uid)).getStatusCode().value());

        user.setStripeCustomerId("cus_1");
        when(stripe.portalUrl("cus_1", "https://app.test/settings")).thenReturn("https://stripe.test/p");
        assertEquals("https://stripe.test/p", billing.portal(uid));
    }

    // ---- webhook ----

    @Test
    void completedSubscriptionSetsThePlanCustomerAndSubscription() throws Exception {
        billing.handle(event("evt_1", "checkout.session.completed", session("subscription", "pro", ",\"subscription\":\"sub_1\",\"payment_status\":\"paid\"")));

        assertEquals("pro", user.getPlan());
        assertEquals(NOW, user.getPlanStartedAt());
        assertEquals("cus_1", user.getStripeCustomerId());
        assertEquals("sub_1", user.getStripeSubscriptionId());
        verify(events).claim("evt_1", "checkout.session.completed");
    }

    @Test
    void completedPackNeedsAPaidSessionAndHasNoSubscription() throws Exception {
        billing.handle(event("evt_1", "checkout.session.completed", session("payment", "pack", ",\"payment_status\":\"unpaid\"")));
        assertEquals("free", user.getPlan());

        billing.handle(event("evt_2", "checkout.session.completed", session("payment", "pack", ",\"payment_status\":\"paid\"")));
        assertEquals("pack", user.getPlan());
        assertEquals(NOW, user.getPlanStartedAt());
        assertNull(user.getStripeSubscriptionId());
    }

    @Test
    void aRedeliveredEventIsSkipped() throws Exception {
        when(events.claim("evt_1", "checkout.session.completed")).thenReturn(0);
        billing.handle(event("evt_1", "checkout.session.completed", session("subscription", "pro", ",\"subscription\":\"sub_1\",\"payment_status\":\"paid\"")));

        assertEquals("free", user.getPlan());
        verify(users, never()).save(any());
    }

    @Test
    void anUnknownUserOrPlanChangesNothingButIsStillRecorded() throws Exception {
        when(users.findById(uid)).thenReturn(Optional.empty());
        billing.handle(event("evt_1", "checkout.session.completed", session("subscription", "pro", ",\"subscription\":\"sub_1\",\"payment_status\":\"paid\"")));
        billing.handle(event("evt_2", "checkout.session.completed", "{\"id\":\"cs_2\",\"mode\":\"subscription\",\"client_reference_id\":\"nope\",\"metadata\":{\"plan\":\"pro\"}}"));

        assertEquals("free", user.getPlan());
        verify(users, never()).save(any());
    }

    @Test
    void subscriptionEndedDropsASubscriberToFree() throws Exception {
        user.setPlan("pro");
        user.setStripeSubscriptionId("sub_1");
        when(users.findByStripeSubscriptionId("sub_1")).thenReturn(Optional.of(user));

        billing.handle(event("evt_1", "customer.subscription.deleted", "{\"id\":\"sub_1\"}"));

        assertEquals("free", user.getPlan());
        assertNull(user.getPlanStartedAt());
        assertNull(user.getStripeSubscriptionId());
    }

    @Test
    void aPackBuyerIsNeverDowngradedBySubscriptionEvents() throws Exception {
        user.setPlan("pack");   // a pack has no subscription id, so no event can find this user
        when(users.findByStripeSubscriptionId("sub_old")).thenReturn(Optional.empty());

        billing.handle(event("evt_1", "customer.subscription.deleted", "{\"id\":\"sub_old\"}"));

        assertEquals("pack", user.getPlan());
    }

    @Test
    void subscriptionUpdatedFollowsAPortalPlanChangeAndKeepsPlanWhilePastDue() throws Exception {
        user.setPlan("starter");
        user.setStripeSubscriptionId("sub_1");
        when(users.findByStripeSubscriptionId("sub_1")).thenReturn(Optional.of(user));

        billing.handle(event("evt_1", "customer.subscription.updated",
                "{\"id\":\"sub_1\",\"status\":\"past_due\",\"items\":{\"data\":[{\"price\":{\"id\":\"price_p\"}}]}}"));

        assertEquals("pro", user.getPlan());
    }

    @Test
    void subscriptionUpdatedToCanceledOrUnpaidDropsToFree() throws Exception {
        user.setPlan("pro");
        user.setStripeSubscriptionId("sub_1");
        when(users.findByStripeSubscriptionId("sub_1")).thenReturn(Optional.of(user));

        billing.handle(event("evt_1", "customer.subscription.updated", "{\"id\":\"sub_1\",\"status\":\"unpaid\"}"));

        assertEquals("free", user.getPlan());
    }

    @Test
    void anUpdateBeforeCheckoutCompletedIsIgnored() throws Exception {
        when(users.findByStripeSubscriptionId("sub_1")).thenReturn(Optional.empty());

        billing.handle(event("evt_1", "customer.subscription.updated", "{\"id\":\"sub_1\",\"status\":\"active\"}"));

        assertEquals("free", user.getPlan());
    }

    @Test
    void aDelayedPaymentIsGrantedWhenItSucceedsNotWhenTheSessionCompletes() throws Exception {
        billing.handle(event("evt_1", "checkout.session.completed", session("subscription", "pro", ",\"subscription\":\"sub_1\",\"payment_status\":\"unpaid\"")));
        assertEquals("free", user.getPlan());

        billing.handle(event("evt_2", "checkout.session.async_payment_succeeded", session("subscription", "pro", ",\"subscription\":\"sub_1\",\"payment_status\":\"paid\"")));
        assertEquals("pro", user.getPlan());
    }

    @Test
    void aSecondPaidCheckoutCancelsTheNewSubscriptionAndKeepsTheFirst() throws Exception {
        user.setPlan("starter");
        user.setStripeSubscriptionId("sub_1");

        billing.handle(event("evt_1", "checkout.session.completed", session("subscription", "pro", ",\"subscription\":\"sub_2\",\"payment_status\":\"paid\"")));

        assertEquals("starter", user.getPlan());
        assertEquals("sub_1", user.getStripeSubscriptionId());
        verify(stripe).cancelSubscription("sub_2");
    }

    @Test
    void aDuplicatePackWhileSubscribedIsLeftForARefundNotApplied() throws Exception {
        user.setPlan("pro");
        user.setStripeSubscriptionId("sub_1");

        billing.handle(event("evt_1", "checkout.session.completed", session("payment", "pack", ",\"payment_status\":\"paid\"")));

        assertEquals("pro", user.getPlan());
        verify(stripe, never()).cancelSubscription(any());
    }

    @Test
    void aPackBuyerWhoSubscribesMovesToTheSubscription() throws Exception {
        user.setPlan("pack");

        billing.handle(event("evt_1", "checkout.session.completed", session("subscription", "pro", ",\"subscription\":\"sub_1\",\"payment_status\":\"paid\"")));

        assertEquals("pro", user.getPlan());
        assertEquals("sub_1", user.getStripeSubscriptionId());
    }

    @Test
    void aFullyRefundedPackIsTakenBackButAPartialRefundIsNot() throws Exception {
        user.setPlan("pack");
        user.setStripeCustomerId("cus_1");
        when(users.findByStripeCustomerId("cus_1")).thenReturn(Optional.of(user));

        billing.handle(event("evt_1", "charge.refunded", "{\"customer\":\"cus_1\",\"refunded\":false}"));
        assertEquals("pack", user.getPlan());

        billing.handle(event("evt_2", "charge.refunded", "{\"customer\":\"cus_1\",\"refunded\":true}"));
        assertEquals("free", user.getPlan());
    }

    @Test
    void aRefundNeverDowngradesASubscriber() throws Exception {
        user.setPlan("pro");
        user.setStripeCustomerId("cus_1");
        when(users.findByStripeCustomerId("cus_1")).thenReturn(Optional.of(user));

        billing.handle(event("evt_1", "charge.refunded", "{\"customer\":\"cus_1\",\"refunded\":true}"));

        assertEquals("pro", user.getPlan());
    }

    @Test
    void anyStatusOtherThanActiveTrialingOrPastDueDropsToFree() throws Exception {
        user.setPlan("pro");
        user.setStripeSubscriptionId("sub_1");
        when(users.findByStripeSubscriptionId("sub_1")).thenReturn(Optional.of(user));

        billing.handle(event("evt_1", "customer.subscription.updated", "{\"id\":\"sub_1\",\"status\":\"past_due\"}"));
        assertEquals("pro", user.getPlan());

        billing.handle(event("evt_2", "customer.subscription.updated", "{\"id\":\"sub_1\",\"status\":\"paused\"}"));
        assertEquals("free", user.getPlan());
    }
}
