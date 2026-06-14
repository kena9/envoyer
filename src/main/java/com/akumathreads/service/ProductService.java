package com.akumathreads.service;

import com.akumathreads.model.Product;
import com.akumathreads.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Business logic for product catalogue management.
 *
 * <p>All reads use the class-level {@code readOnly = true} transaction.
 * All writes override with {@code readOnly = false, rollbackFor = Exception.class}.
 *
 * <p>Soft deletes are handled transparently by the {@code @SQLDelete} annotation
 * on {@link Product} — calling {@code productRepository.delete(product)} issues a
 * {@code UPDATE products SET deleted = true} rather than a physical DELETE.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    // ── Read operations ──────────────────────────────────────────────────────

    /**
     * Returns all active, non-deleted products with their variants pre-loaded.
     * Safe for the shop listing page — no N+1 queries.
     */
    public List<Product> findAllActiveWithVariants() {
        return productRepository.findAllActiveWithVariants();
    }

    /**
     * Finds one product by ID with variants eagerly loaded.
     * Used for the product detail page and add-to-cart flow.
     *
     * @param id PK of the product
     * @return {@link Optional} with the product + variants, or empty
     */
    public Optional<Product> findByIdWithVariants(Long id) {
        return productRepository.findByIdWithVariants(id);
    }

    /**
     * Finds products matching the given keyword (case-insensitive name search).
     * Used by the shop search bar.
     *
     * @param keyword search term
     * @return matching active products
     */
    public List<Product> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return productRepository.findByActiveTrueOrderByCreatedDateDesc();
        }
        return productRepository.findByNameContainingIgnoreCaseAndActiveTrue(keyword.strip());
    }

    /**
     * Filters active products by category.
     *
     * @param category the category to filter on
     * @return matching active products
     */
    public List<Product> findByCategory(Product.Category category) {
        return productRepository.findByCategoryAndActiveTrue(category);
    }

    /**
     * Admin: retrieves all soft-deleted products for the trash/restore view.
     */
    public List<Product> findAllSoftDeleted() {
        return productRepository.findAllSoftDeleted();
    }

    // ── Write operations ─────────────────────────────────────────────────────

    /**
     * Persists a new product or updates an existing one.
     *
     * @param product the product to save (new entity if id is null, update otherwise)
     * @return the saved product with generated ID populated
     */
    @Transactional(readOnly = false, rollbackFor = Exception.class)
    public Product save(Product product) {
        return productRepository.save(product);
    }

    /**
     * Soft-deletes a product by PK.
     *
     * <p>Calls {@code repository.delete()} which the {@code @SQLDelete} on {@link Product}
     * rewrites to {@code UPDATE products SET deleted = true WHERE id = ?}.
     * The product's {@code active} flag is also set to {@code false} so it is
     * consistently excluded from all active-product queries.
     *
     * @param productId PK of the product to soft-delete
     * @throws EntityNotFoundException if no product with the given ID exists
     */
    @Transactional(readOnly = false, rollbackFor = Exception.class)
    public void softDeleteProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Product not found: " + productId));
        product.setActive(false);
        // Calling delete() triggers @SQLDelete — issues UPDATE SET deleted=true, not a physical DELETE
        productRepository.delete(product);
    }

    /**
     * Restores a soft-deleted product by setting {@code deleted = false} via a
     * direct field update followed by a save. The {@code @SQLRestriction} is a
     * Hibernate-level filter that cannot be bypassed with JPQL for a single entity
     * read, so we use a native query approach via {@link ProductRepository#findAllSoftDeleted()}
     * combined with an ID lookup on the raw result.
     *
     * @param productId PK of the product to restore
     * @throws EntityNotFoundException if no soft-deleted product with the given ID exists
     */
    @Transactional(readOnly = false, rollbackFor = Exception.class)
    public Product restoreProduct(Long productId) {
        Product product = productRepository.findAllSoftDeleted().stream()
                .filter(p -> p.getId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(
                        "Soft-deleted product not found: " + productId));
        product.setDeleted(false);
        product.setActive(true);
        return productRepository.save(product);
    }
}
