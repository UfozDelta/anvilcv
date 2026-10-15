package com.resumepipeline.billing;

import java.util.UUID;

/** The two Stripe calls billing needs. An interface so tests run without the network. */
public interface StripeApi {

    /**
     * @param mode       "subscription" (starter, pro) or "payment" (pack)
     * @param customerId an existing Stripe customer, or null to let Checkout create one from email
     */
    record CheckoutRequest(String mode, String priceId, String plan, UUID userId,
                           String customerId, String email, String successUrl, String cancelUrl) {}

    /** Returns the hosted Checkout page to send the browser to. */
    String checkoutUrl(CheckoutRequest request);

    /** Ends a subscription at once. Used to undo a duplicate checkout. */
    void cancelSubscription(String subscriptionId);

    /** Returns the customer portal page, where a subscriber changes or cancels their plan. */
    String portalUrl(String customerId, String returnUrl);
}
