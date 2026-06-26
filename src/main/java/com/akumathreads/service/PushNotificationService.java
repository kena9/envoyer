package com.akumathreads.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages browser push notification subscriptions and sends admin alerts.
 *
 * <p>Subscriptions are stored in-memory for the dev build.
 * In production, persist to the database and use a real VAPID-signed Web Push library.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {

    /** In-memory store: endpoint → subscription JSON payload. */
    private final Map<String, String> subscriptions = new ConcurrentHashMap<>();

    /**
     * Placeholder VAPID public key.
     * Generate a real pair with: npx web-push generate-vapid-keys
     * Then set via @Value("${vapid.public-key}") in production.
     */
    private static final String VAPID_PUBLIC_KEY =
            "BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U";

    // ── Push Controller API ───────────────────────────────────────────────────

    /** Returns the VAPID public key for browser push subscription setup. */
    public String getVapidPublicKey() {
        return VAPID_PUBLIC_KEY;
    }

    /**
     * Registers a browser push subscription with its crypto keys.
     * In production, persist these to the database.
     */
    public void saveSubscription(String endpoint, String p256dh, String auth) {
        String json = String.format(
                "{\"endpoint\":\"%s\",\"p256dh\":\"%s\",\"auth\":\"%s\"}",
                endpoint, p256dh, auth);
        subscriptions.put(endpoint, json);
        log.info("[Push] Subscribed endpoint: {}", endpoint);
    }

    /** Removes a push subscription (user opted out or subscription expired). */
    public void removeSubscription(String endpoint) {
        subscriptions.remove(endpoint);
        log.info("[Push] Unsubscribed endpoint: {}", endpoint);
    }

    // ── Admin notification API ────────────────────────────────────────────────

    /** Active subscription count — shown on admin notification dashboard. */
    public int subscriptionCount() {
        return subscriptions.size();
    }

    /** Convenience overload — no click URL. */
    public void sendToAll(String title, String body) {
        sendToAll(title, body, null);
    }

    /**
     * Sends a push notification to all subscribers.
     * TODO: implement VAPID-signed Web Push POST to each endpoint for production.
     */
    public void sendToAll(String title, String body, String url) {
        if (subscriptions.isEmpty()) {
            log.info("[Push] No subscribers — skipping push for: {}", title);
            return;
        }
        log.info("[Push] Sending '{}' to {} subscriber(s)", title, subscriptions.size());
        subscriptions.forEach((endpoint, payload) ->
                log.debug("[Push]  → {}", endpoint));
    }
}
