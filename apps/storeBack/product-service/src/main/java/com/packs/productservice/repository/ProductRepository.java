package com.packs.productservice.repository;

import com.packs.productservice.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

	Page<Product> findByCategoryAndDeletedAtIsNull(String category, Pageable pageable);

	Page<Product> findAllByDeletedAtIsNull(Pageable pageable);

	@Query("""
		SELECT p FROM Product p
		WHERE p.deletedAt IS NULL
		  AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
		   OR LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%'))
		   OR LOWER(p.category) LIKE LOWER(CONCAT('%', :search, '%')))
		""")
	Page<Product> search(String search, Pageable pageable);

	@Query("""
		SELECT p FROM Product p
		WHERE p.deletedAt IS NULL
		  AND p.category = :category
		  AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
		   OR LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%'))
		   OR LOWER(p.category) LIKE LOWER(CONCAT('%', :search, '%')))
		""")
	Page<Product> searchByCategory(String category, String search, Pageable pageable);

	@Query("""
		SELECT p FROM Product p
		WHERE p.deletedAt IS NULL
		  AND (:category IS NULL OR p.category = :category)
		  AND (:query IS NULL
		       OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
		       OR LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%'))
		       OR LOWER(p.category) LIKE LOWER(CONCAT('%', :query, '%')))
		  AND (:priceMin IS NULL OR p.price >= :priceMin)
		  AND (:priceMax IS NULL OR p.price <= :priceMax)
		  AND (:inStock IS NULL OR (:inStock = true AND p.stockQuantity > 0))
		""")
	Page<Product> search(
		@Param("query") String query,
		@Param("category") String category,
		@Param("priceMin") BigDecimal priceMin,
		@Param("priceMax") BigDecimal priceMax,
		@Param("inStock") Boolean inStock,
		Pageable pageable);

	@Transactional
	@Modifying
	@Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :quantity WHERE p.id = :productId AND p.stockQuantity >= :quantity AND p.deletedAt IS NULL")
	int decrementStock(UUID productId, int quantity);

	@Transactional
	@Modifying
	@Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity + :quantity, p.updatedAt = :updatedAt WHERE p.id = :productId AND p.deletedAt IS NULL")
	int incrementStock(UUID productId, int quantity, Instant updatedAt);

	Optional<Product> findByIdAndDeletedAtIsNull(UUID id);
}