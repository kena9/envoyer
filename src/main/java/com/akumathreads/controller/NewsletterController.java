package com.akumathreads.controller;

import com.akumathreads.service.NewsletterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Handles newsletter subscribe / unsubscribe requests.
 *
 * <p>The subscribe endpoint is called via fetch() from the popup JS,
 * so it returns JSON instead of a redirect.
 */
@Controller
@RequiredArgsConstructor
public class NewsletterController {

    private final NewsletterService newsletterService;

    /** Called by the popup form via fetch(). Returns JSON. */
    @PostMapping("/subscribe")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> subscribe(@RequestParam String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Please enter a valid email address."));
        }

        String code = newsletterService.subscribe(email);
        if (code != null) {
            return ResponseEntity.ok(Map.of(
                    "success",  true,
                    "code",     code,
                    "message",  "You're in! Your 10% off code: " + code
            ));
        } else {
            // Already subscribed
            return ResponseEntity.ok(Map.of(
                    "success",  true,
                    "code",     "WELCOME10",
                    "message",  "You're already subscribed! Your code: WELCOME10"
            ));
        }
    }

    /** One-click unsubscribe link from emails. */
    @GetMapping("/unsubscribe")
    public String unsubscribe(@RequestParam String email) {
        newsletterService.unsubscribe(email);
        return "newsletter/unsubscribed";
    }
}
