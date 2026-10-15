package com.resumepipeline.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.resumepipeline.auth.AppUser;
import com.resumepipeline.auth.AppUserRepository;
import com.resumepipeline.quota.Plan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Turns Stripe payments into plans. Checkout and the portal are hosted by Stripe; the webhook is the
 * only thing that changes {@code app_user.plan}, so a user cannot grant themselves a plan by hand.
 *
 * <p>A subscriber (starter, pro) keeps the plan until Stripe ends the subscription, which for a
 * cancel-at-period-end happens at the period end, then drops to free. A pack is a one-off payment and
 * never downgrades. Only a free or pack account may start a checkout; changing or cancelling goes through the
 * portal, so a user cannot end up with a pack on top of a live subscription.
 */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final AppUserRepository users;
    private final StripeEventRepository events;
    private final StripeApi stripe;
    private final Clock clock;
    private final String returnOrigin;
    private final Map<Plan, String> prices;

    @Autowired
    public BillingService(AppUserRepository users, StripeEventRepository events, StripeApi stripe,
                          @Value("${stripe.return-origin:}") String returnOrigin,
                          @Value("${stripe.price.starter:}") String starter,
                          @Value("${stripe.price.pro:}") String pro,
                          @Value("${stripe.price.pack:}") String pack) {
        this(users, events, stripe, Clock.systemUTC(), returnOrigin,
                Map.of(Plan.STARTER, starter, Plan.PRO, pro, Plan.PACK, pack));
    }

    BillingService(AppUserRepository users, StripeEventRepository events, StripeApi stripe, Clock clock,
                   String returnOrigin, Map<Plan, String> prices) {
        this.users = users;
        this.events = events;
        this.stripe = stripe;
        this.clock = clock;
        this.returnOrigin = returnOrigin;
        this.prices = prices;
    }

    /** The Checkout page for a plan. Free and used-up pack accounts only; subscribers use the portal. */
    public String checkout(UUID userId, String planSlug) {
        Plan plan = Plan.of(planSlug);
        if (plan == Plan.FREE) throw new IllegalArgumentException("Unknown plan: " + planSlug);
        String price = prices.getOrDefault(plan, "");
        if (price.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Billing is not set up.");

        AppUser u = users.findById(userId).orElseThrow();
        // A pack buyer may buy again (or subscribe) once it is used up; a subscriber changes plan in the portal.
        if (!Plan.FREE.slug().equals(u.getPlan()) && !Plan.PACK.slug().equals(u.getPlan())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have a plan. Manage it from billing.");
        }
        return stripe.checkoutUrl(new StripeApi.CheckoutRequest(
                plan == Plan.PACK ? "payment" : "subscription", price, plan.slug(), u.getId(),
                u.getStripeCustomerId(), u.getEmail(),
                returnOrigin + "/settings?billing=success", returnOrigin + "/pricing"));
    }

    /** The customer portal. Needs a Stripe customer, so only someone who has paid. */
    public String portal(UUID userId) {
        AppUser u = users.findById(userId).orElseThrow();
        if (u.getStripeCustomerId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No billing account yet.");
        }
        return stripe.portalUrl(u.getStripeCustomerId(), returnOrigin + "/settings");
    }

    /** Applies one verified webhook event. Idempotent by event id; types we do not use are ignored. */
    @Transactional
    public void handle(JsonNode event) {
        String id = event.path("id").asText("");
        String type = event.path("type").asText("");
        // Claimed first and in this transaction: a redelivery racing this one loses the insert, and a
        // failure below rolls the claim back so the retry runs again.
        if (id.isEmpty() || events.claim(id, type) == 0) return;
        JsonNode obj = event.path("data").path("object");
        switch (type) {
            // async_payment_succeeded is the same session finally paid (SEPA, ACH and other delayed methods)
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> completed(obj);
            case "customer.subscription.updated" -> subscriptionUpdated(obj);
            case "customer.subscription.deleted" -> subscriptionEnded(obj);
            case "charge.refunded" -> { if (obj.path("refunded").asBoolean()) refunded(obj); }
            case "charge.dispute.created" -> log.error("STRIPE_DISPUTE charge={} handle by hand", obj.path("charge").asText());
            default -> { /* not ours */ }
        }
    }

    private void completed(JsonNode s) {
        // A delayed method completes the session unpaid; async_payment_succeeded delivers it again as paid.
        String paid = s.path("payment_status").asText();
        if (!paid.equals("paid") && !paid.equals("no_payment_required")) return;
        Plan plan = Plan.of(s.path("metadata").path("plan").asText());
        if (plan == Plan.FREE) {
            log.warn("STRIPE_EVENT checkout without a plan session={}", s.path("id").asText());
            return;
        }
        AppUser u = userOf(s.path("client_reference_id").asText(""));
        if (u == null) {
            log.warn("STRIPE_EVENT checkout for unknown user session={}", s.path("id").asText());
            return;
        }

        String sub = s.path("subscription").asText("");
        String live = u.getStripeSubscriptionId();
        if (live != null && !live.equals(sub)) {
            // Paid twice (two checkouts open at once). Keep the first; end the new subscription. A
            // duplicate pack is left in the log to refund by hand.
            log.error("STRIPE_DUPLICATE user={} session={} subscription={} refund or cancel by hand if not ended",
                    u.getId(), s.path("id").asText(), sub);
            if (!sub.isEmpty()) {
                try { stripe.cancelSubscription(sub); }
                catch (RuntimeException e) { log.error("STRIPE_DUPLICATE cancel failed subscription={}", sub, e); }
            }
            return;
        }

        u.setPlan(plan.slug());
        u.setPlanStartedAt(clock.instant());
        String customer = s.path("customer").asText("");
        if (!customer.isEmpty()) u.setStripeCustomerId(customer);
        u.setStripeSubscriptionId(sub.isEmpty() ? null : sub);
        users.save(u);
        log.info("STRIPE_PLAN user={} plan={}", u.getId(), plan.slug());
    }

    private void subscriptionUpdated(JsonNode sub) {
        AppUser u = users.findByStripeSubscriptionId(sub.path("id").asText()).orElse(null);
        if (u == null) return;   // checkout.session.completed has not arrived yet; it sets the plan itself
        switch (sub.path("status").asText()) {
            case "active", "trialing", "past_due" -> {
                // past_due keeps the plan while Stripe retries the card; set the dashboard's final retry to cancel.
                // A change made in the portal shows up as a different price.
                Plan now = planOfPrice(sub.path("items").path("data").path(0).path("price").path("id").asText(""));
                if (now != null && !now.slug().equals(u.getPlan())) {
                    u.setPlan(now.slug());
                    users.save(u);
                    log.info("STRIPE_PLAN user={} plan={}", u.getId(), now.slug());
                }
            }
            default -> downgrade(u);   // canceled, unpaid, paused, incomplete_expired, anything new
        }
    }

    /** A fully refunded pack is taken back. A subscription ends through its own events. */
    private void refunded(JsonNode charge) {
        String customer = charge.path("customer").asText("");
        if (customer.isEmpty()) return;
        users.findByStripeCustomerId(customer)
                .filter(u -> Plan.PACK.slug().equals(u.getPlan()))
                .ifPresent(this::downgrade);
    }

    private void subscriptionEnded(JsonNode sub) {
        users.findByStripeSubscriptionId(sub.path("id").asText()).ifPresent(this::downgrade);
    }

    private void downgrade(AppUser u) {
        u.setPlan(Plan.FREE.slug());
        u.setPlanStartedAt(null);
        u.setStripeSubscriptionId(null);
        users.save(u);
        log.info("STRIPE_PLAN user={} plan=free", u.getId());
    }

    private Plan planOfPrice(String priceId) {
        if (priceId.isEmpty()) return null;
        for (Plan p : new Plan[] {Plan.STARTER, Plan.PRO}) {
            if (priceId.equals(prices.get(p))) return p;
        }
        return null;
    }

    private AppUser userOf(String ref) {
        try { return users.findById(UUID.fromString(ref)).orElse(null); }
        catch (IllegalArgumentException e) { return null; }
    }
}
