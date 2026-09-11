package com.packs.productservice.repository;

import com.packs.productservice.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

	Page<Product> findByCategory(String category, Pageable pageable);

	@Query("""
		SELECT p FROM Product p
		WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
		   OR LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%'))
		   OR LOWER(p.category) LIKE LOWER(CONCAT('%', :search, '%'))
		""")
	Page<Product> search(String search, Pageable pageable);

	@Query("""
		SELECT p FROM Product p
		WHERE p.category = :category
		  AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
		   OR LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%'))
		   OR LOWER(p.category) LIKE LOWER(CONCAT('%', :search, '%')))
		""")
	Page<Product> searchByCategory(String category, String search, Pageable pageable);

	@Transactional
	@Modifying
	@Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :quantity WHERE p.id = :productId AND p.stockQuantity >= :quantity")
	int decrementStock(UUID productId, int quantity);

	@Transactional
	@Modifying
	@Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity + :quantity, p.updatedAt = :updatedAt WHERE p.id = :productId")
	int incrementStock(UUID productId, int quantity, java.time.Instant updatedAt);

	Optional<Product> findById(UUID id);
}