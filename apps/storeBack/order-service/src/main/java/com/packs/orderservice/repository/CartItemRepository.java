package com.packs.orderservice.repository;

import com.packs.orderservice.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

	List<CartItem> findByCartId(UUID cartId);

	Optional<CartItem> findByCartIdAndProductId(UUID cartId, UUID productId);

	@Modifying
	void deleteByCartId(UUID cartId);

	@Modifying
	int deleteByCartIdAndProductId(UUID cartId, UUID productId);
}