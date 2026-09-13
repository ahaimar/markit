package com.packs.productservice.repository;

import com.packs.productservice.entity.ProductOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProductOutboxEventRepository extends JpaRepository<ProductOutboxEvent, UUID> {

	List<ProductOutboxEvent> findByPublishedFalseOrderByCreatedAtAsc();

	@Transactional
	@Modifying
	@Query("UPDATE ProductOutboxEvent e SET e.published = true, e.publishedAt = :now WHERE e.id = :id")
	void markPublished(UUID id, Instant now);
}