package com.packs.orderservice.client;

import com.packs.sharedlib.ProductDto;
import com.packs.sharedlib.StockDecrementRequest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "app.product-service.url=http://localhost:18082"
})
class ProductClientContractTest {

    private static final WireMockServer wireMockServer = new WireMockServer(options().port(18082));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.product-service.url", () -> "http://localhost:18082");
    }

    @Autowired
    private ProductClient productClient;

    @BeforeEach
    void setUp() {
        wireMockServer.start();
        WireMock.configureFor("localhost", 18082);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
        wireMockServer.resetAll();
    }

    @Test
    void getProduct_existingProduct_returnsProductDto() {
        String productId = "550e8400-e29b-41d4-a716-446655440000";

        stubFor(get(urlEqualTo("/api/products/" + productId))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "id": "550e8400-e29b-41d4-a716-446655440000",
                        "name": "Ethiopian Yirgacheffe",
                        "description": "Bright, fruity, floral",
                        "price": 18.99,
                        "category": "coffee",
                        "stockQuantity": 50
                    }
                    """)));

        ProductDto result = productClient.getProduct(productId);

        assertNotNull(result);
        assertEquals(productId, result.id());
        assertEquals("Ethiopian Yirgacheffe", result.name());
        assertEquals(new BigDecimal("18.99"), result.price());
        assertEquals("coffee", result.category());
        assertEquals(50, result.stockQuantity());
    }

    @Test
    void getProduct_notFound_throwsException() {
        String productId = "nonexistent-id";

        stubFor(get(urlEqualTo("/api/products/" + productId))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"ERR_PRODUCT_NOT_FOUND\",\"message\":\"Product not found\",\"details\":null}")));

        assertThrows(Exception.class, () -> productClient.getProduct(productId));
    }

    @Test
    void decrementStock_validRequest_callsProductService() {
        String productId = "550e8400-e29b-41d4-a716-446655440000";

        stubFor(post(urlEqualTo("/api/products/" + productId + "/stock/decrement"))
            .withHeader("Content-Type", containing("application/json"))
            .willReturn(aResponse()
                .withStatus(204)));

        productClient.decrementStock(productId, new StockDecrementRequest(2));

        verify(postRequestedFor(urlEqualTo("/api/products/" + productId + "/stock/decrement"))
            .withHeader("Content-Type", containing("application/json")));
    }

    @Test
    void incrementStock_validRequest_callsProductService() {
        String productId = "550e8400-e29b-41d4-a716-446655440000";

        stubFor(post(urlEqualTo("/api/products/" + productId + "/stock/increment"))
            .withHeader("Content-Type", containing("application/json"))
            .willReturn(aResponse()
                .withStatus(204)));

        productClient.incrementStock(productId, new StockDecrementRequest(5));

        verify(postRequestedFor(urlEqualTo("/api/products/" + productId + "/stock/increment"))
            .withHeader("Content-Type", containing("application/json")));
    }

    @Test
    void decrementStock_insufficientStock_throwsException() {
        String productId = "550e8400-e29b-41d4-a716-446655440000";

        stubFor(post(urlEqualTo("/api/products/" + productId + "/stock/decrement"))
            .willReturn(aResponse()
                .withStatus(409)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"ERR_INSUFFICIENT_STOCK\",\"message\":\"Insufficient stock\",\"details\":null}")));

        assertThrows(Exception.class, () -> productClient.decrementStock(productId, new StockDecrementRequest(999)));
    }
}
