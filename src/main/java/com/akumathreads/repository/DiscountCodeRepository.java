package com.akumathreads.repository;

import com.akumathreads.model.DiscountCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DiscountCodeRepository extends JpaRepository<DiscountCode, Long> {

    Optional<DiscountCode> findByCodeIgnoreCase(String code);

    List<DiscountCode> findAllByOrderByCreatedAtDesc();

    List<DiscountCode> findByActiveTrue();

    @Modifying
    @Query("UPDATE DiscountCode d SET d.usedCount = d.usedCount + 1 WHERE d.code = :code")
    void incrementUsedCount(@Param("code") String code);
}
