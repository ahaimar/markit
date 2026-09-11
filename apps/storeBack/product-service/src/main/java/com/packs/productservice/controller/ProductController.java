package com.packs.productservice.controller;

import com.packs.productservice.dto.ProductRequest;
import com.packs.productservice.dto.StockDecrementRequest;
import com.packs.productservice.service.ProductService;
import com.packs.sharedlib.ApiException;
import com.packs.sharedlib.PageResponse;
import com.packs.sharedlib.ProductDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {

	private final ProductService productService;

	public ProductController(ProductService productService) {
		this.productService = productService;
	}

	@GetMapping
	public ResponseEntity<PageResponse<ProductDto>> list(
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int pageSize,
		@RequestParam(required = false) String category,
		@RequestParam(required = false) String search
	) {
		return ResponseEntity.ok(productService.list(page, pageSize, category, search));
	}

	@GetMapping("/{productId}")
	public ResponseEntity<ProductDto> getProduct(@PathVariable UUID productId) {
		return ResponseEntity.ok(productService.getById(productId));
	}

	@PostMapping
	public ResponseEntity<ProductDto> create(
		@RequestHeader(value = "X-User-Roles", required = false) String rolesHeader,
		@Valid @RequestBody ProductRequest request) {
		requireAdmin(rolesHeader);
		return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
	}

	@PostMapping("/{productId}/stock/decrement")
	public ResponseEntity<Void> decrementStock(@PathVariable UUID productId, @RequestBody @Valid StockDecrementRequest request) {
		productService.decrementStock(productId, request.quantity());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{productId}/stock/increment")
	public ResponseEntity<Void> incrementStock(@PathVariable UUID productId, @RequestBody @Valid StockDecrementRequest request) {
		productService.incrementStock(productId, request.quantity());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{productId}/stock")
	public ResponseEntity<Map<String, Integer>> getStock(@PathVariable UUID productId) {
		return ResponseEntity.ok(Map.of("stockQuantity", productService.getStock(productId)));
	}

	@PutMapping("/{productId}/stock")
	public ResponseEntity<Void> updateStock(
		@RequestHeader(value = "X-User-Roles", required = false) String rolesHeader,
		@PathVariable UUID productId,
		@RequestBody Map<String, Integer> body) {
		requireAdmin(rolesHeader);
		productService.updateStock(productId, body.get("stockQuantity"));
		return ResponseEntity.noContent().build();
	}

	private void requireAdmin(String rolesHeader) {
		if (rolesHeader == null || !List.of(rolesHeader.split(",")).contains("ADMIN")) {
			throw new ApiException("ERR_FORBIDDEN", "ADMIN role required", HttpStatus.FORBIDDEN);
		}
	}
}