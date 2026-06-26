package com.akumathreads.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Email subscriber who signed up via the 10% popup or checkout.
 * Used for drop announcements and deal notifications.
 */
@Entity
@Table(name = "newsletter_subscribers")
@Getter @Setter @NoArgsConstructor
public class NewsletterSubscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, updatable = false)
    private LocalDateTime subscribedAt = LocalDateTime.now();

    /** False if they clicked unsubscribe. Never delete — preserve history. */
    @Column(nullable = false)
    private boolean active = true;

    public NewsletterSubscriber(String email) {
        this.email = email.toLowerCase().strip();
    }
}
