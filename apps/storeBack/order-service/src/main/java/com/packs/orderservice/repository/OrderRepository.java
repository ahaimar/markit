package com.packs.orderservice.repository;

import com.packs.orderservice.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

	List<Order> findByUserIdOrderByCreatedAtDesc(UUID userId);

	Page<Order> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}