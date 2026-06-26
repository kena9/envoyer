package com.akumathreads.repository;

import com.akumathreads.model.SiteContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SiteContentRepository extends JpaRepository<SiteContent, Long> {

    Optional<SiteContent> findByContentKey(String contentKey);

    List<SiteContent> findByPageOrderByIdAsc(String page);

    boolean existsByContentKey(String contentKey);
}
