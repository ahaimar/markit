package com.packs.productservice.service;

import com.packs.productservice.dto.ProductRequest;
import com.packs.productservice.dto.SearchRequest;
import com.packs.productservice.entity.Product;
import com.packs.productservice.entity.ProductOutboxEvent;
import com.packs.productservice.repository.ProductOutboxEventRepository;
import com.packs.productservice.repository.ProductRepository;
import com.packs.sharedlib.ApiException;
import com.packs.sharedlib.PageResponse;
import com.packs.sharedlib.ProductDeletedEvent;
import com.packs.sharedlib.ProductDto;
import com.packs.sharedlib.ProductUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

@Service
public class ProductService {

	private static final Logger log = LoggerFactory.getLogger(ProductService.class);

	private final ProductRepository productRepository;
	private final ProductOutboxEventRepository productOutboxEventRepository;
	private final ObjectMapper objectMapper;

	public ProductService(
		ProductRepository productRepository,
		ProductOutboxEventRepository productOutboxEventRepository,
		ObjectMapper objectMapper) {
		this.productRepository = productRepository;
		this.productOutboxEventRepository = productOutboxEventRepository;
		this.objectMapper = objectMapper;
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
			productPage = productRepository.findByCategoryAndDeletedAtIsNull(cat, pageable);
		} else {
			productPage = productRepository.findAllByDeletedAtIsNull(pageable);
		}

		return toPage(productPage);
	}

	@Transactional(readOnly = true)
	@Cacheable(cacheNames = "products", key = "'search:' + #request")
	public PageResponse<ProductDto> search(SearchRequest request) {
		int page = Math.max(request.page(), 0);
		int pageSize = Math.min(Math.max(request.pageSize() == 0 ? 20 : request.pageSize(), 1), 100);
		Pageable pageable = PageRequest.of(page, pageSize);

		Page<Product> productPage = productRepository.search(
			blankToNull(request.query()),
			blankToNull(request.category()),
			request.priceMin(),
			request.priceMax(),
			request.inStock(),
			pageable);

		return toPage(productPage);
	}

	@Transactional(readOnly = true)
	@Cacheable(cacheNames = "products", key = "'byId:' + #productId")
	public ProductDto getById(UUID productId) {
		Product product = findActive(productId);
		return toDto(product);
	}

	@Transactional
	@CacheEvict(cacheNames = "products", allEntries = true)
	public ProductDto create(ProductRequest request) {
		Instant now = Instant.now();
		Product product = new Product();
		product.setName(request.name());
		product.setDescription(request.description());
		product.setPrice(request.price());
		product.setCategory(request.category());
		product.setStockQuantity(request.stockQuantity());
		product.setCreatedAt(now);
		product.setUpdatedAt(now);

		return toDto(productRepository.save(product));
	}

	@Transactional
	@CacheEvict(cacheNames = "products", allEntries = true)
	public ProductDto update(UUID productId, ProductRequest request) {
		Product product = findActive(productId);
		product.setName(request.name());
		product.setDescription(request.description());
		product.setPrice(request.price());
		product.setCategory(request.category());
		product.setStockQuantity(request.stockQuantity());
		product.setUpdatedAt(Instant.now());

		try {
			Product saved = productRepository.save(product);
			enqueueProductUpdated(saved);
			return toDto(saved);
		} catch (OptimisticLockingFailureException ex) {
			throw new ApiException("ERR_CONCURRENT_UPDATE", "Product was modified concurrently, retry the request", HttpStatus.CONFLICT, ex);
		}
	}

	@Transactional
	@CacheEvict(cacheNames = "products", allEntries = true)
	public void delete(UUID productId) {
		Product product = findActive(productId);
		Instant now = Instant.now();
		product.setDeletedAt(now);
		product.setUpdatedAt(now);
		try {
			productRepository.save(product);
			enqueueProductDeleted(productId);
		} catch (OptimisticLockingFailureException ex) {
			throw new ApiException("ERR_CONCURRENT_UPDATE", "Product was modified concurrently, retry the request", HttpStatus.CONFLICT, ex);
		}
	}

	@Transactional
	@CacheEvict(cacheNames = "products", allEntries = true)
	public void decrementStock(UUID productId, int quantity) {
		int updated = productRepository.decrementStock(productId, quantity);
		if (updated == 0) {
			if (productRepository.findByIdAndDeletedAtIsNull(productId).isEmpty()) {
				throw new ApiException("ERR_PRODUCT_NOT_FOUND", "Product not found: " + productId, HttpStatus.NOT_FOUND);
			}
			throw new ApiException("ERR_INSUFFICIENT_STOCK", "Insufficient stock for product: " + productId, HttpStatus.CONFLICT);
		}
	}

	@Transactional
	@CacheEvict(cacheNames = "products", allEntries = true)
	public void incrementStock(UUID productId, int quantity) {
		findActive(productId);
		productRepository.incrementStock(productId, quantity, Instant.now());
	}

	@Transactional(readOnly = true)
	public int getStock(UUID productId) {
		return findActive(productId).getStockQuantity();
	}

	@Transactional
	@CacheEvict(cacheNames = "products", allEntries = true)
	public void updateStock(UUID productId, int stockQuantity) {
		Product product = findActive(productId);
		product.setStockQuantity(stockQuantity);
		product.setUpdatedAt(Instant.now());
		try {
			productRepository.save(product);
		} catch (OptimisticLockingFailureException ex) {
			throw new ApiException("ERR_CONCURRENT_UPDATE", "Product was modified concurrently, retry the request", HttpStatus.CONFLICT, ex);
		}
	}

	private void enqueueProductUpdated(Product product) {
		UUID eventId = UUID.randomUUID();
		ProductUpdatedEvent event = new ProductUpdatedEvent(
			eventId.toString(),
			product.getId().toString(),
			product.getName(),
			product.getDescription(),
			product.getPrice(),
			product.getCategory(),
			product.getStockQuantity(),
			Instant.now());
		enqueue("ProductUpdated", event);
	}

	private void enqueueProductDeleted(UUID productId) {
		ProductDeletedEvent event = new ProductDeletedEvent(UUID.randomUUID().toString(), productId.toString(), Instant.now());
		enqueue("ProductDeleted", event);
	}

	private void enqueue(String eventType, Object event) {
		try {
			ProductOutboxEvent outbox = new ProductOutboxEvent();
			outbox.setEventType(eventType);
			outbox.setPayload(objectMapper.writeValueAsString(event));
			outbox.setPublished(false);
			outbox.setCreatedAt(Instant.now());
			productOutboxEventRepository.save(outbox);
			log.info("Enqueued {} outbox event", eventType);
		} catch (JacksonException ex) {
			log.error("Failed to serialize {} event", eventType, ex);
		}
	}

	private Product findActive(UUID productId) {
		return productRepository.findByIdAndDeletedAtIsNull(productId)
			.orElseThrow(() -> new ApiException("ERR_PRODUCT_NOT_FOUND", "Product not found", HttpStatus.NOT_FOUND));
	}

	private PageResponse<ProductDto> toPage(Page<Product> productPage) {
		return PageResponse.of(
			productPage.getContent().stream().map(this::toDto).toList(),
			productPage.getNumber(),
			productPage.getSize(),
			productPage.getTotalElements()
		);
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.trim();
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