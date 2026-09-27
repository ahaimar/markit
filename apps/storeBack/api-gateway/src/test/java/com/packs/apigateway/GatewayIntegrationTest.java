package com.packs.apigateway;

import com.packs.sharedlib.JwtService;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIntegrationTest {

    private static final WireMockServer userMock = new WireMockServer(options().port(28081));
    private static final WireMockServer productMock = new WireMockServer(options().port(28082));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.rate-limit.per-ip", () -> "100");
        registry.add("app.rate-limit.per-user", () -> "1000");
        registry.add("app.rate-limit.window-seconds", () -> "60");
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        userMock.start();
        productMock.start();
    }

    @AfterEach
    void tearDown() {
        userMock.stop();
        productMock.stop();
        userMock.resetAll();
        productMock.resetAll();
    }

    @Test
    void publicEndpoint_noAuth_returnsOk() {
        stubFor(post(urlEqualTo("/api/users/register"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"accessToken\":\"token\",\"refreshToken\":\"refresh\",\"user\":{\"id\":\"1\",\"email\":\"test@test.com\",\"name\":\"Test\",\"role\":\"CUSTOMER\"}}")));

        webTestClient.post()
            .uri("/api/users/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"email\":\"test@test.com\",\"password\":\"Password1\",\"name\":\"Test User\"}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.accessToken").exists()
            .jsonPath("$.user.email").valueEquals("test@test.com");
    }

    @Test
    void protectedEndpoint_noAuth_returnsUnauthorized() {
        stubFor(get(urlEqualTo("/api/products"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"content\":[],\"page\":0,\"size\":10,\"totalElements\":0}")));

        webTestClient.get()
            .uri("/api/products")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.code").valueEquals("ERR_UNAUTHORIZED");
    }

    @Test
    void protectedEndpoint_invalidToken_returnsUnauthorized() {
        webTestClient.get()
            .uri("/api/products")
            .header("Authorization", "Bearer invalid-token")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.code").valueEquals("ERR_UNAUTHORIZED");
    }

    @Test
    void protectedEndpoint_validToken_routesToService() {
        String userId = "550e8400-e29b-41d4-a716-446655440000";
        String token = jwtService.createToken(userId, "test@test.com", List.of("CUSTOMER"));

        stubFor(get(urlEqualTo("/api/products"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"content\":[{\"id\":\"1\",\"name\":\"Coffee\",\"price\":9.99,\"stockQuantity\":10,\"category\":\"coffee\",\"description\":\"Good coffee\"}],\"page\":0,\"size\":10,\"totalElements\":1}")));

        webTestClient.get()
            .uri("/api/products")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].name").valueEquals("Coffee");
    }

    @Test
    void protectedEndpoint_validToken_injectsUserHeaders() {
        String userId = "550e8400-e29b-41d4-a716-446655440000";
        String token = jwtService.createToken(userId, "admin@test.com", List.of("ADMIN"));

        stubFor(get(urlEqualTo("/api/users/" + userId))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + userId + "\",\"email\":\"admin@test.com\",\"name\":\"Admin\",\"role\":\"ADMIN\"}")));

        webTestClient.get()
            .uri("/api/users/" + userId)
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.role").valueEquals("ADMIN");
    }

    @Test
    void swaggerDocs_noAuth_returnsOk() {
        webTestClient.get()
            .uri("/v3/api-docs")
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void rateLimit_exceededLimit_returnsTooManyRequests() {
        String userId = "550e8400-e29b-41d4-a716-446655440000";
        String token = jwtService.createToken(userId, "test@test.com", List.of("CUSTOMER"));

        stubFor(get(urlEqualTo("/api/products"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"content\":[],\"page\":0,\"size\":10,\"totalElements\":0}")));

        for (int i = 0; i < 101; i++) {
            webTestClient.get()
                .uri("/api/products")
                .header("Authorization", "Bearer " + token)
                .exchange();
        }

        webTestClient.get()
            .uri("/api/products")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
            .expectBody()
            .jsonPath("$.code").valueEquals("ERR_RATE_LIMITED");
    }
}
