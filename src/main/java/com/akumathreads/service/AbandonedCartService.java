package com.akumathreads.service;

import com.akumathreads.model.CartItem;
import com.akumathreads.model.User;
import com.akumathreads.repository.CartItemRepository;
import com.akumathreads.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects abandoned carts and sends a single recovery email per user.
 *
 * <p><strong>Rules:</strong>
 * <ul>
 *   <li>Cart is "abandoned" if the user has items added more than 2 hours ago
 *       and has NOT placed an order in the last 7 days.</li>
 *   <li>One email per user max — tracked via {@link CartItem#getRecoveryEmailSentAt()}.</li>
 *   <li>Emails are not sent for carts older than 7 days (stale / probably intentional).</li>
 *   <li>Each user gets a unique 10% recovery discount code.</li>
 * </ul>
 *
 * <p>Runs every hour via {@code @Scheduled(fixedDelay = 3_600_000)}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AbandonedCartService {

    /** How long before a cart is considered abandoned */
    private static final int ABANDON_HOURS = 2;

    /** Don't email carts older than this — user has clearly moved on */
    private static final int MAX_ABANDON_DAYS = 7;

    private final CartItemRepository    cartItemRepository;
    private final OrderRepository       orderRepository;
    private final EmailService          emailService;
    private final DiscountCodeService   discountCodeService;

    /**
     * Main scheduled job — runs once per hour.
     * Finds abandoned carts, excludes recent orderers, sends recovery emails.
     */
    @Scheduled(fixedDelay = 3_600_000) // every hour
    @Transactional
    public void processAbandonedCarts() {
        LocalDateTime now      = LocalDateTime.now();
        LocalDateTime cutoff   = now.minusHours(ABANDON_HOURS);    // items added before this = abandoned
        LocalDateTime maxAge   = now.minusDays(MAX_ABANDON_DAYS);  // items older than this = skip

        // Load items abandoned within the window — recovery email not yet sent
        List<CartItem> abandoned = cartItemRepository.findAbandonedItemsBefore(cutoff)
                .stream()
                .filter(ci -> ci.getAddedAt().isAfter(maxAge)) // skip stale carts
                .collect(Collectors.toList());

        if (abandoned.isEmpty()) {
            log.debug("[AbandonedCart] No abandoned carts to process.");
            return;
        }

        // Group by user
        Map<User, List<CartItem>> byUser = new LinkedHashMap<>();
        for (CartItem item : abandoned) {
            byUser.computeIfAbsent(item.getUser(), k -> new ArrayList<>()).add(item);
        }

        // Find users who ordered recently — exclude them
        Set<Long> recentBuyers = new HashSet<>(
                orderRepository.findUserIdsWithRecentOrders(now.minusDays(MAX_ABANDON_DAYS)));

        int emailsSent = 0;
        for (Map.Entry<User, List<CartItem>> entry : byUser.entrySet()) {
            User user = entry.getKey();

            // Skip users who ordered recently (they're not really abandoned)
            if (recentBuyers.contains(user.getId())) {
                log.debug("[AbandonedCart] Skipping recent buyer: {}", user.getEmail());
                continue;
            }

            List<CartItem> userItems = entry.getValue();

            try {
                // Generate a unique single-use recovery code for this user
                String recoveryCode = discountCodeService.generateRecoveryCode().getCode();

                // Send the email
                emailService.sendAbandonedCartEmail(
                        user.getEmail(),
                        user.getName() != null ? user.getName() : "there",
                        userItems,
                        recoveryCode);

                // Mark all their items as emailed so we don't re-send
                for (CartItem item : userItems) {
                    item.setRecoveryEmailSentAt(now);
                    cartItemRepository.save(item);
                }

                log.info("[AbandonedCart] Recovery email sent → {} ({} items, code: {})",
                        user.getEmail(), userItems.size(), recoveryCode);
                emailsSent++;

            } catch (Exception e) {
                log.error("[AbandonedCart] Failed to process cart for {}: {}",
                        user.getEmail(), e.getMessage());
            }
        }

        if (emailsSent > 0) {
            log.info("[AbandonedCart] Processed {} abandoned cart(s) this run.", emailsSent);
        }
    }
}
