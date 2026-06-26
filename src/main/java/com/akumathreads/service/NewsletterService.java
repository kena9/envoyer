package com.akumathreads.service;

import com.akumathreads.model.NewsletterSubscriber;
import com.akumathreads.repository.NewsletterSubscriberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages newsletter subscriptions and the welcome 10% discount flow.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NewsletterService {

    private final NewsletterSubscriberRepository subscriberRepository;
    private final EmailService                   emailService;
    private final DiscountCodeService            discountCodeService;

    // ── Reads ─────────────────────────────────────────────────────────────────

    public List<NewsletterSubscriber> findAllActive() {
        return subscriberRepository.findAllByActiveTrue();
    }

    /** Active subscriber count — shown on admin notification dashboard. */
    public long countActive() {
        return subscriberRepository.findAllByActiveTrue().size();
    }

    /** All active email addresses — used for blast emails. */
    public List<String> getAllActiveEmails() {
        return subscriberRepository.findAllByActiveTrue().stream()
                .map(NewsletterSubscriber::getEmail)
                .collect(Collectors.toList());
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    /**
     * Subscribes an email address.
     *
     * @return the generated 10% welcome code, or {@code null} if already subscribed
     */
    @Transactional
    public String subscribe(String email) {
        if (email == null || email.isBlank()) return null;
        String normalised = email.strip().toLowerCase();

        if (subscriberRepository.existsByEmail(normalised)) {
            log.debug("[Newsletter] Already subscribed: {}", normalised);
            return null; // caller shows "already subscribed" message
        }

        subscriberRepository.save(new NewsletterSubscriber(normalised));

        String code = null;
        try {
            code = discountCodeService.generateWelcomeCode().getCode();
            emailService.sendWelcomeEmail(normalised, code);
            log.info("[Newsletter] New subscriber + welcome code sent: {}", normalised);
        } catch (Exception e) {
            log.warn("[Newsletter] Welcome email failed for {}: {}", normalised, e.getMessage());
        }
        return code;
    }

    /** Marks a subscriber as inactive. Does nothing if email is unknown. */
    @Transactional
    public void unsubscribe(String email) {
        if (email == null || email.isBlank()) return;
        subscriberRepository.findByEmail(email.strip().toLowerCase())
                .ifPresent(sub -> {
                    sub.setActive(false);
                    subscriberRepository.save(sub);
                    log.info("[Newsletter] Unsubscribed: {}", email);
                });
    }
}
