package com.akumathreads.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Key-value store for editable site text.
 * Keys follow dot-notation: page.section.field
 * e.g. "home.hero.eyebrow", "global.ticker.drop"
 */
@Entity
@Table(name = "site_content")
@Getter @Setter @NoArgsConstructor
public class SiteContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique dot-notation key, e.g. "home.hero.eyebrow" */
    @Column(name = "content_key", unique = true, nullable = false, length = 120)
    private String contentKey;

    /** The actual text value stored for this key */
    @Column(name = "content_value", columnDefinition = "TEXT", nullable = false)
    private String contentValue;

    /** Human-readable label shown in the admin UI */
    @Column(name = "label", length = 120, nullable = false)
    private String label;

    /** Page grouping for admin UI ("Global", "Home", "About") */
    @Column(name = "page", length = 50, nullable = false)
    private String page;

    /** true = render as textarea; false = single-line input */
    @Column(name = "multiline", nullable = false)
    private boolean multiline = false;

    /** Auto-updated on every save */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public SiteContent(String contentKey, String contentValue, String label, String page, boolean multiline) {
        this.contentKey   = contentKey;
        this.contentValue = contentValue;
        this.label        = label;
        this.page         = page;
        this.multiline    = multiline;
    }
}
