package com.akumathreads.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_variants")
@Getter @Setter @NoArgsConstructor
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Size size;

    @Column(nullable = false)
    private int stockQty = 0;

    /**
     * Printful sync variant ID for this size.
     * Set via the admin Printful center after linking/importing from Printful.
     * Used when submitting orders to Printful for fulfillment.
     */
    private Long printfulSyncVariantId;

    public enum Size {
        XS, S, M, L, XL, XXL
    }
}
