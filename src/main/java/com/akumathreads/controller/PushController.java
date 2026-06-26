package com.akumathreads.controller;

import com.akumathreads.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST endpoints for Web Push notification management.
 *
 * <ul>
 *   <li>GET  /push/vapid-public-key  — browser fetches the VAPID public key to subscribe</li>
 *   <li>POST /push/subscribe         — browser saves its subscription after user grants permission</li>
 *   <li>DELETE /push/subscribe       — browser removes subscription on unsubscribe</li>
 * </ul>
 */
@RestController
@RequestMapping("/push")
@RequiredArgsConstructor
public class PushController {

    private final PushNotificationService pushService;

    @GetMapping("/vapid-public-key")
    public ResponseEntity<Map<String, String>> vapidPublicKey() {
        return ResponseEntity.ok(Map.of("publicKey", pushService.getVapidPublicKey()));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, String>> subscribe(
            @RequestParam String endpoint,
            @RequestParam String p256dh,
            @RequestParam String auth) {

        pushService.saveSubscription(endpoint, p256dh, auth);
        return ResponseEntity.ok(Map.of("status", "subscribed"));
    }

    @DeleteMapping("/subscribe")
    public ResponseEntity<Map<String, String>> unsubscribe(@RequestParam String endpoint) {
        pushService.removeSubscription(endpoint);
        return ResponseEntity.ok(Map.of("status", "unsubscribed"));
    }
}
