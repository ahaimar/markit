package com.packs.orderservice.client;

import com.packs.orderservice.dto.StockDecrementRequest;
import com.packs.sharedlib.ApiException;
import com.packs.sharedlib.ProductDto;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ProductClientFallbackFactory implements FallbackFactory<ProductClient> {

	private static final Logger log = LoggerFactory.getLogger(ProductClientFallbackFactory.class);

	@Override
	public ProductClient create(Throwable cause) {
		log.warn("ProductClient fallback invoked: {}", cause.getMessage());
		return new ProductClient() {
			@Override
			public ProductDto getProduct(String productId) {
				throw translate(cause, "Product not reachable: " + productId);
			}

			@Override
			public void decrementStock(String productId, StockDecrementRequest request) {
				throw translate(cause, "Stock decrement not executed: " + productId);
			}

			@Override
			public void incrementStock(String productId, StockDecrementRequest request) {
				throw translate(cause, "Stock restore not executed: " + productId);
			}
		};
	}

	private ApiException translate(Throwable cause, String message) {
		if (cause instanceof FeignException fe) {
			int status = fe.status();
			if (status == HttpStatus.NOT_FOUND.value()) {
				return new ApiException("ERR_PRODUCT_NOT_FOUND", "Product not found", HttpStatus.NOT_FOUND, cause);
			}
			if (status == HttpStatus.CONFLICT.value()) {
				return new ApiException("ERR_INSUFFICIENT_STOCK", "Insufficient stock", HttpStatus.CONFLICT, cause);
			}
		}
		return new ApiException("ERR_PRODUCT_SERVICE_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE, cause);
	}
}