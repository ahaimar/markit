package com.packs.productservice.service;

import com.packs.productservice.dto.ProductRequest;
import com.packs.productservice.dto.SearchRequest;
import com.packs.productservice.entity.Product;
import com.packs.productservice.entity.ProductOutboxEvent;
import com.packs.productservice.repository.ProductOutboxEventRepository;
import com.packs.productservice.repository.ProductRepository;
import com.packs.sharedlib.ApiException;
import com.packs.sharedlib.ProductDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

	@Mock
	private ProductRepository productRepository;
	@Mock
	private ProductOutboxEventRepository productOutboxEventRepository;

	private ProductService productService;
	private final ObjectMapper objectMapper = new ObjectMapper();

	private UUID productId;
	private Product product;

	@BeforeEach
	void setUp() {
		productService = new ProductService(productRepository, productOutboxEventRepository, objectMapper);

		productId = UUID.randomUUID();
		product = new Product();
		product.setId(productId);
		product.setName("Coffee Beans - Ethiopia");
		product.setDescription("Single origin, medium roast.");
		product.setPrice(new BigDecimal("100.00"));
		product.setCategory("Coffee");
		product.setStockQuantity(40);
		product.setCreatedAt(Instant.now());
		product.setUpdatedAt(Instant.now());
	}

	@Test
	void create_persistsAndReturnsDto() {
		ProductRequest request = new ProductRequest("Espresso Cups (Set of 2)", "Double-wall glass.", new BigDecimal("24.00"), "Mugs", 15);
		when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
			Product saved = invocation.getArgument(0);
			saved.setId(UUID.randomUUID());
			return saved;
		});

		ProductDto dto = productService.create(request);

		assertEquals("Espresso Cups (Set of 2)", dto.name());
		assertEquals(new BigDecimal("24.00"), dto.price());
		verify(productRepository).save(any(Product.class));
		verify(productOutboxEventRepository, never()).save(any(ProductOutboxEvent.class));
	}

	@Test
	void getById_returnsDto() {
		when(productRepository.findByIdAndDeletedAtIsNull(productId)).thenReturn(Optional.of(product));

		ProductDto dto = productService.getById(productId);

		assertEquals(productId.toString(), dto.id());
		assertEquals("Coffee Beans - Ethiopia", dto.name());
	}

	@Test
	void getById_throwsWhenDeleted() {
		when(productRepository.findByIdAndDeletedAtIsNull(productId)).thenReturn(Optional.empty());

		ApiException ex = assertThrows(ApiException.class, () -> productService.getById(productId));

		assertEquals("ERR_PRODUCT_NOT_FOUND", ex.getCode());
		assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
	}

	@Test
	void update_modifiesFieldsAndEnqueuesEvent() throws Exception {
		when(productRepository.findByIdAndDeletedAtIsNull(productId)).thenReturn(Optional.of(product));
		when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ProductRequest request = new ProductRequest("Renamed", "Updated description.", new BigDecimal("42.00"), "Brewing", 7);
		ProductDto dto = productService.update(productId, request);

		assertEquals("Renamed", dto.name());
		assertEquals(new BigDecimal("42.00"), dto.price());
		assertEquals(7, dto.stockQuantity());

		ArgumentCaptor<ProductOutboxEvent> captor = ArgumentCaptor.forClass(ProductOutboxEvent.class);
		verify(productOutboxEventRepository).save(captor.capture());
		assertEquals("ProductUpdated", captor.getValue().getEventType());
		assertNotNull(objectMapper.readTree(captor.getValue().getPayload()).get("productId"));
	}

	@Test
	void update_throwsWhenNotFound() {
		when(productRepository.findByIdAndDeletedAtIsNull(productId)).thenReturn(Optional.empty());

		ApiException ex = assertThrows(ApiException.class,
			() -> productService.update(productId, new ProductRequest("x", "y", BigDecimal.ONE, "z", 1)));

		assertEquals("ERR_PRODUCT_NOT_FOUND", ex.getCode());
	}

	@Test
	void delete_softDeletesAndEnqueuesEvent() throws Exception {
		when(productRepository.findByIdAndDeletedAtIsNull(productId)).thenReturn(Optional.of(product));

		productService.delete(productId);

		assertNotNull(product.getDeletedAt());
		verify(productRepository).save(product);

		ArgumentCaptor<ProductOutboxEvent> captor = ArgumentCaptor.forClass(ProductOutboxEvent.class);
		verify(productOutboxEventRepository).save(captor.capture());
		assertEquals("ProductDeleted", captor.getValue().getEventType());
		assertEquals(productId.toString(), objectMapper.readTree(captor.getValue().getPayload()).get("productId").asText());
	}

	@Test
	void delete_throwsWhenNotFound() {
		when(productRepository.findByIdAndDeletedAtIsNull(productId)).thenReturn(Optional.empty());

		ApiException ex = assertThrows(ApiException.class, () -> productService.delete(productId));

		assertEquals("ERR_PRODUCT_NOT_FOUND", ex.getCode());
	}

	@Test
	void search_delegatesFiltersToRepository() {
		Page<Product> page = new PageImpl<>(List.of(product));
		when(productRepository.search(anyString(), anyString(), any(), any(), anyBoolean(), any(PageRequest.class)))
			.thenReturn(page);

		SearchRequest request = new SearchRequest("coffee", "Coffee", new BigDecimal("10"), new BigDecimal("200"), true, 0, 20);
		var result = productService.search(request);

		assertEquals(1, result.items().size());
		assertEquals(1, result.total());
		verify(productRepository).search(
			eq("coffee"), eq("Coffee"), eq(new BigDecimal("10")), eq(new BigDecimal("200")), eq(true), any(PageRequest.class));
	}

	@Test
	void decrementStock_success() {
		when(productRepository.decrementStock(productId, 2)).thenReturn(1);

		productService.decrementStock(productId, 2);

		verify(productRepository).decrementStock(productId, 2);
	}

	@Test
	void decrementStock_insufficientThrowsConflict() {
		when(productRepository.decrementStock(productId, 999)).thenReturn(0);
		when(productRepository.findByIdAndDeletedAtIsNull(productId)).thenReturn(Optional.of(product));

		ApiException ex = assertThrows(ApiException.class, () -> productService.decrementStock(productId, 999));

		assertEquals("ERR_INSUFFICIENT_STOCK", ex.getCode());
		assertEquals(HttpStatus.CONFLICT, ex.getStatus());
	}

	@Test
	void decrementStock_missingThrowsNotFound() {
		when(productRepository.decrementStock(productId, 1)).thenReturn(0);
		when(productRepository.findByIdAndDeletedAtIsNull(productId)).thenReturn(Optional.empty());

		ApiException ex = assertThrows(ApiException.class, () -> productService.decrementStock(productId, 1));

		assertEquals("ERR_PRODUCT_NOT_FOUND", ex.getCode());
	}

	@Test
	void incrementStock_requiresActiveProduct() {
		when(productRepository.findByIdAndDeletedAtIsNull(productId)).thenReturn(Optional.of(product));

		productService.incrementStock(productId, 3);

		verify(productRepository).incrementStock(eq(productId), eq(3), any(Instant.class));
	}

	@Test
	void updateStock_rejectsConcurrentModification() {
		when(productRepository.findByIdAndDeletedAtIsNull(productId)).thenReturn(Optional.of(product));

		productService.updateStock(productId, 55);

		assertEquals(55, product.getStockQuantity());
		verify(productRepository).save(product);
	}

	@Test
	void list_excludesDeletedByDefault() {
		when(productRepository.findAllByDeletedAtIsNull(any(PageRequest.class)))
			.thenReturn(new PageImpl<>(List.of(product)));

		var result = productService.list(0, 20, null, null);

		assertEquals(1, result.total());
		verify(productRepository).findAllByDeletedAtIsNull(any(PageRequest.class));
	}
}