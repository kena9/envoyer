package com.akumathreads.repository;

import com.akumathreads.model.CartItem;
import com.akumathreads.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByUser(User user);
    Optional<CartItem> findByUserAndVariantId(User user, Long variantId);
    void deleteByUser(User user);
    int countByUser(User user);

    /**
     * Finds all cart items added before {@code cutoff} where no recovery email
     * has been sent yet. Groups conceptually by user — the scheduler processes
     * users from the returned items.
     */
    @Query("SELECT ci FROM CartItem ci " +
           "JOIN FETCH ci.user u " +
           "JOIN FETCH ci.variant v " +
           "JOIN FETCH v.product " +
           "WHERE ci.addedAt < :cutoff " +
           "AND ci.recoveryEmailSentAt IS NULL " +
           "ORDER BY u.id ASC, ci.addedAt ASC")
    List<CartItem> findAbandonedItemsBefore(@Param("cutoff") LocalDateTime cutoff);

    /** All items belonging to a user where recovery email has not been sent. */
    List<CartItem> findByUserAndRecoveryEmailSentAtIsNull(User user);
}
