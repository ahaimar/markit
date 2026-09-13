package com.packs.orderservice.client;

import com.packs.sharedlib.ProductDto;
import com.packs.sharedlib.StockDecrementRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "product-service", url = "${app.product-service.url}", fallbackFactory = ProductClientFallbackFactory.class)
public interface ProductClient {

	@GetMapping("/api/products/{productId}")
	ProductDto getProduct(@PathVariable("productId") String productId);

	@PostMapping("/api/products/{productId}/stock/decrement")
	void decrementStock(@PathVariable("productId") String productId, @RequestBody StockDecrementRequest request);

	@PostMapping("/api/products/{productId}/stock/increment")
	void incrementStock(@PathVariable("productId") String productId, @RequestBody StockDecrementRequest request);
}