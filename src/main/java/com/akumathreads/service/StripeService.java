package com.akumathreads.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Stripe payment processing service.
 *
 * <p>Uses the Stripe Java SDK to create PaymentIntents server-side.
 * The client (browser) confirms the payment using the clientSecret.
 * The server verifies the PaymentIntent status before fulfilling the order.
 */
@Service
@Slf4j
public class StripeService {

    @Value("${stripe.secret-key}")
    private String secretKey;

    @Value("${stripe.publishable-key}")
    private String publishableKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
        log.info("[Stripe] Initialized — mode: {}", secretKey.contains("_test_") ? "TEST" : "LIVE");
    }

    public String getPublishableKey() {
        return publishableKey;
    }

    /**
     * Creates a PaymentIntent for the given total amount.
     *
     * @param totalAmount the total charge in dollars (subtotal + shipping + tax)
     * @param customerEmail customer email for Stripe receipt
     * @return the PaymentIntent (contains clientSecret for browser + id for verification)
     */
    public PaymentIntent createPaymentIntent(BigDecimal totalAmount, String customerEmail)
            throws StripeException {

        // Stripe amounts are in cents (smallest currency unit)
        long amountCents = totalAmount.multiply(BigDecimal.valueOf(100))
                                      .setScale(0, RoundingMode.HALF_UP)
                                      .longValue();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountCents)
                .setCurrency("usd")
                .setReceiptEmail(customerEmail)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build()
                )
                .build();

        PaymentIntent intent = PaymentIntent.create(params);
        log.info("[Stripe] PaymentIntent created: {} for ${}", intent.getId(), totalAmount);
        return intent;
    }

    /**
     * Retrieves a PaymentIntent from Stripe, confirms it has "succeeded",
     * and verifies the captured amount matches our server-computed expected total.
     *
     * @param paymentIntentId  the PI id from the client
     * @param expectedTotal    server-side total in dollars
     * @return the PaymentIntent if all checks pass, null otherwise
     */
    public PaymentIntent retrieveAndVerify(String paymentIntentId, java.math.BigDecimal expectedTotal)
            throws StripeException {

        PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);

        if (!"succeeded".equals(intent.getStatus())) {
            log.warn("[Stripe] PI {} not succeeded — status: {}", paymentIntentId, intent.getStatus());
            return null;
        }

        // Amount-match check: prevents a tampered client paying less
        if (expectedTotal != null) {
            long expectedCents = expectedTotal
                    .multiply(java.math.BigDecimal.valueOf(100))
                    .setScale(0, java.math.RoundingMode.HALF_UP)
                    .longValue();
            if (!intent.getAmount().equals(expectedCents)) {
                log.warn("[Stripe] PI {} amount mismatch — expected {} cents, got {} cents",
                        paymentIntentId, expectedCents, intent.getAmount());
                return null;
            }
        }

        log.info("[Stripe] PI {} verified — ${}", paymentIntentId, expectedTotal);
        return intent;
    }
}