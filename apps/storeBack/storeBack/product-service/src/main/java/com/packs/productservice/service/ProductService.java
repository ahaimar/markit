package com.packs.productservice.service;

import com.packs.productservice.dto.ProductRequest;
import com.packs.productservice.entity.Product;
import com.packs.productservice.repository.ProductRepository;
import com.packs.sharedlib.ApiException;
import com.packs.sharedlib.PageResponse;
import com.packs.sharedlib.ProductDto;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ProductService {

	private final ProductRepository productRepository;

	public ProductService(ProductRepository productRepository) {
		this.productRepository = productRepository;
	}

	@Transactional(readOnly = true)
	@Cacheable(cacheNames = "products", key = "'list:' + #page + ':' + #pageSize + ':' + (#category == null ? '' : #category) + ':' + (#search == null ? '' : #search)")
	public PageResponse<ProductDto> list(int page, int pageSize, String category, String search) {
		Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(pageSize, 1), 100));
		Page<Product> productPage;

		String term = (search == null || search.isBlank()) ? null : search.trim();
		String cat = (category == null || category.isBlank()) ? null : category.trim();

		if (cat != null && term != null) {
			productPage = productRepository.searchByCategory(cat, term, pageable);
		} else if (term != null) {
			productPage = productRepository.search(term, pageable);
		} else if (cat != null) {
			productPage = productRepository.findByCategory(cat, pageable);
		} else {
			productPage = productRepository.findAll(pageable);
		}

		return PageResponse.of(
			productPage.getContent().stream().map(this::toDto).toList(),
			productPage.getNumber(),
			productPage.getSize(),
			productPage.getTotalElements()
		);
	}

	@Transactional(readOnly = true)
	@Cacheable(cacheNames = "products", key = "'byId:' + #productId")
	public ProductDto getById(UUID productId) {
		Product product = productRepository.findById(productId)
			.orElseThrow(() -> new ApiException("ERR_PRODUCT_NOT_FOUND", "Product not found", HttpStatus.NOT_FOUND));
		return toDto(product);
	}

	@Transactional
	@CacheEvict(cacheNames = "products", allEntries = true)
	public ProductDto create(ProductRequest request) {
		Product product = new Product();
		product.setName(request.name());
		product.setDescription(request.description());
		product.setPrice(request.price());
		product.setCategory(request.category());
		product.setStockQuantity(request.stockQuantity());
		product.setCreatedAt(Instant.now());
		product.setUpdatedAt(Instant.now());

		Product saved = productRepository.save(product);
		return toDto(saved);
	}

	@Transactional
	@CacheEvict(cacheNames = "products", allEntries = true)
	public void decrementStock(UUID productId, int quantity) {
		int updated = productRepository.decrementStock(productId, quantity);
		if (updated == 0) {
			boolean exists = productRepository.findById(productId).isPresent();
			if (!exists) {
				throw new ApiException("ERR_PRODUCT_NOT_FOUND", "Product not found: " + productId, HttpStatus.NOT_FOUND);
			}
			throw new ApiException("ERR_INSUFFICIENT_STOCK", "Insufficient stock for product: " + productId, HttpStatus.CONFLICT);
		}
	}

	@Transactional
	@CacheEvict(cacheNames = "products", allEntries = true)
	public void incrementStock(UUID productId, int quantity) {
		boolean exists = productRepository.findById(productId).isPresent();
		if (!exists) {
			throw new ApiException("ERR_PRODUCT_NOT_FOUND", "Product not found: " + productId, HttpStatus.NOT_FOUND);
		}
		productRepository.incrementStock(productId, quantity, Instant.now());
	}

	@Transactional(readOnly = true)
	public int getStock(UUID productId) {
		Product product = productRepository.findById(productId)
			.orElseThrow(() -> new ApiException("ERR_PRODUCT_NOT_FOUND", "Product not found", HttpStatus.NOT_FOUND));
		return product.getStockQuantity();
	}

	@Transactional
	@CacheEvict(cacheNames = "products", allEntries = true)
	public void updateStock(UUID productId, int stockQuantity) {
		Product product = productRepository.findById(productId)
			.orElseThrow(() -> new ApiException("ERR_PRODUCT_NOT_FOUND", "Product not found", HttpStatus.NOT_FOUND));
		product.setStockQuantity(stockQuantity);
		product.setUpdatedAt(Instant.now());
		try {
			productRepository.save(product);
		} catch (OptimisticLockingFailureException ex) {
			throw new ApiException("ERR_CONCURRENT_UPDATE", "Product was modified concurrently, retry the request", HttpStatus.CONFLICT, ex);
		}
	}

	private ProductDto toDto(Product product) {
		return new ProductDto(
			product.getId().toString(),
			product.getName(),
			product.getDescription(),
			product.getPrice(),
			product.getCategory(),
			product.getStockQuantity()
		);
	}
}