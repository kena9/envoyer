package com.akumathreads.controller;

import com.akumathreads.service.OrderService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Stripe webhook receiver.
 *
 * <p>This endpoint is deliberately excluded from CSRF protection (Stripe can't
 * send our CSRF token) and from authentication (it's an inbound server-to-server
 * call). Authenticity is verified instead via Stripe's HMAC signature
 * ({@code Stripe-Signature} header + {@code stripe.webhook-secret}).
 *
 * <p>The only event we act on is {@code payment_intent.succeeded}, which
 * transitions the matching order from PAID → PROCESSING so it's ready for
 * Printful fulfillment. All other events are acknowledged (200) and ignored.
 *
 * <p>Stripe retries webhooks on any non-2xx response, so we never return 4xx/5xx
 * for legitimate events — only for invalid signatures.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final OrderService orderService;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @PostMapping("/stripe/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        // ── Signature verification + event parsing ──────────────────────────────
        // stripe-java 24+ shades Gson internally, so we cannot parse raw JSON
        // outside the SDK. Instead: when the webhook secret is not configured
        // (local dev), we skip processing and return 200 so Stripe doesn't
        // retry. In real dev/CI, run `stripe listen --forward-to localhost:8080/stripe/webhook`
        // which provides a real whsec_ signing secret — set it in application-local.properties.
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("[Webhook] stripe.webhook-secret not set — event ignored (configure via Stripe CLI for local testing)");
            return ResponseEntity.ok("ok");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("[Webhook] Invalid Stripe signature: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        log.info("[Webhook] Received event: {}", event.getType());

        // ── Handle payment_intent.succeeded ──────────────────────────────────
        if ("payment_intent.succeeded".equals(event.getType())) {
            Optional<StripeObject> dataObj = event.getDataObjectDeserializer().getObject();
            if (dataObj.isPresent() && dataObj.get() instanceof PaymentIntent intent) {
                log.info("[Webhook] payment_intent.succeeded — PI={} amount={}¢",
                        intent.getId(), intent.getAmountReceived());
                orderService.markPaidByPaymentIntent(intent.getId());
            } else {
                log.warn("[Webhook] Could not deserialize PaymentIntent from event {}", event.getId());
            }
        }

        // Return 200 for all other event types — Stripe must not retry
        return ResponseEntity.ok("ok");
    }
}
