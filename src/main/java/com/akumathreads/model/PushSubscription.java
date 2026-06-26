package com.akumathreads.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Browser push subscription received from the Push API.
 * Stored so the server can send push notifications to the device.
 */
@Entity
@Table(name = "push_subscriptions")
@Getter @Setter @NoArgsConstructor
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The push service endpoint URL (unique per browser/device). */
    @Column(nullable = false, unique = true, length = 512)
    private String endpoint;

    /** Browser-generated public key (base64url). */
    @Column(nullable = false, length = 256)
    private String p256dh;

    /** Browser-generated auth secret (base64url). */
    @Column(nullable = false, length = 64)
    private String auth;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
