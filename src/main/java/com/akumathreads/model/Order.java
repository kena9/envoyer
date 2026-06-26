package com.akumathreads.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter @Setter @NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    // ── Shipping snapshot ─────────────────────────────────────────────────────
    @Column(nullable = false)
    private String shipName;

    @Column(nullable = false)
    private String shipAddress;

    private String shipCity;
    private String shipState;
    private String shipZip;
    private String shipCountry;

    // ── Pricing breakdown ─────────────────────────────────────────────────────
    @Column(precision = 10, scale = 2)
    private BigDecimal shippingCost = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    /** Discount applied at checkout (may be zero). */
    @Column(precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** Coupon code used, if any. */
    private String couponCode;

    // ── Payment ───────────────────────────────────────────────────────────────
    /** Stripe PaymentIntent ID — stored after Stripe confirms payment. */
    @Column(unique = true)
    private String paymentIntentId;

    // ── Printful fulfillment ──────────────────────────────────────────────────
    /** Printful order ID returned after successful push to their API. */
    private String printfulOrderId;

    /** Carrier tracking number — populated when Printful marks order shipped. */
    private String trackingNumber;

    /** Printful-provided tracking URL. */
    private String trackingUrl;

    // ── Timestamps ────────────────────────────────────────────────────────────
    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── Line items ────────────────────────────────────────────────────────────
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OrderItem> items;

    public enum Status {
        PENDING, PAID, PROCESSING, SHIPPED, DELIVERED, CANCELLED
    }
}
