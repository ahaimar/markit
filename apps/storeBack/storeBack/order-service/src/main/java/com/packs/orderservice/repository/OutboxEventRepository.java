package com.packs.orderservice.repository;

import com.packs.orderservice.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

	List<OutboxEvent> findByPublishedFalseOrderByCreatedAtAsc();

	@Transactional
	@Modifying
	@Query("UPDATE OutboxEvent e SET e.published = true, e.publishedAt = :now WHERE e.id = :id")
	void markPublished(UUID id, java.time.Instant now);
}