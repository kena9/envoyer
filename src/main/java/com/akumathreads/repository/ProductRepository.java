package com.akumathreads.repository;

import com.akumathreads.model.Product;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Product repository with N+1 prevention via entity graphs and JPQL JOIN FETCHes.
 *
 * <p>All standard {@code findBy*} methods benefit from the {@code @SQLRestriction("deleted = false")}
 * on the entity, so soft-deleted products are automatically excluded without any
 * additional {@code active = true} predicate.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // ── N+1-safe single-product fetch ─────────────────────────────────────────

    /**
     * Fetches one product together with all its variants in a single SQL query
     * using a JPQL JOIN FETCH — avoids the N+1 problem on the product detail page.
     *
     * <p>EntityGraph alternative (commented out — use whichever style your team prefers):
     * {@code @EntityGraph(attributePaths = {"variants"})}
     *
     * @param productId PK of the product to load
     * @return an {@link Optional} containing the product with variants, or empty
     */
    @Query("SELECT p FROM Product p JOIN FETCH p.variants WHERE p.id = :productId")
    Optional<Product> findByIdWithVariants(@Param("productId") Long productId);

    // ── N+1-safe collection fetch ─────────────────────────────────────────────

    /**
     * Fetches all non-deleted active products with their variants using the
     * {@code Product.withVariants} named entity graph defined on the entity class.
     *
     * <p>The {@code org.hibernate.cacheable} hint marks the query result as
     * eligible for the second-level query cache (requires a second-level cache
     * provider such as EhCache or Caffeine configured in application.properties).
     */
    @EntityGraph(value = "Product.withVariants")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    @Query("SELECT p FROM Product p WHERE p.active = true ORDER BY p.createdDate DESC")
    List<Product> findAllActiveWithVariants();

    // ── Standard filtered finders ─────────────────────────────────────────────

    /**
     * Finds all active (non-soft-deleted) products in the given category.
     * The {@code @SQLRestriction} on {@link Product} already excludes deleted rows,
     * so this method does not need an explicit {@code deleted = false} predicate.
     */
    List<Product> findByCategoryAndActiveTrue(Product.Category category);

    /**
     * Case-insensitive product name search for the shop search bar.
     */
    List<Product> findByNameContainingIgnoreCaseAndActiveTrue(String keyword);

    /**
     * Returns all active products ordered by creation date descending.
     * Used for the default shop listing page.
     */
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<Product> findByActiveTrueOrderByCreatedDateDesc();

    // ── Admin queries (bypass soft-delete filter via native query) ────────────

    /**
     * Retrieves all products including soft-deleted ones for the admin trash view.
     * Uses a native query to bypass the {@code @SQLRestriction} Hibernate filter.
     *
     * <p>This is intentional: the restriction is a static Hibernate annotation
     * on the entity class and cannot be selectively disabled from a JPQL query
     * without a Hibernate {@code Session}-level filter. A native query is the
     * cleanest escape hatch for this specific admin use case.
     */
    @Query(value = "SELECT * FROM products WHERE deleted = true ORDER BY last_modified_date DESC",
           nativeQuery = true)
    List<Product> findAllSoftDeleted();
}
