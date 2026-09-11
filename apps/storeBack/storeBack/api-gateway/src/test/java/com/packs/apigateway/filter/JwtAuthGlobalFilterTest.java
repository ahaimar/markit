package com.packs.apigateway.filter;

import com.packs.sharedlib.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JwtAuthGlobalFilterTest {

	private static final String SECRET = "markit-dev-secret-key-change-me-in-production-0123456789";

	private JwtService jwtService;
	private JwtAuthGlobalFilter filter;

	@BeforeEach
	void setUp() {
		jwtService = new JwtService(SECRET, 900, 604800);
		filter = new JwtAuthGlobalFilter(jwtService);
	}

	@Test
	void publicPathIsForwardedWithoutToken() {
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users/login"));
		AtomicInteger forwarded = new AtomicInteger();

		filter.filter(exchange, e -> {
			forwarded.incrementAndGet();
			return Mono.empty();
		}).block();

		assertEquals(1, forwarded.get(), "public path should reach the chain");
	}


	@Test
	void missingAuthorizationHeader_returns401() {
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products"));

		filter.filter(exchange, next()).block();

		assertEquals(401, exchange.getResponse().getStatusCode().value());
	}

	@Test
	void invalidToken_returns401() {
		MockServerWebExchange exchange = MockServerWebExchange.from(
			MockServerHttpRequest.get("/api/products").header("Authorization", "Bearer not.a.token"));

		filter.filter(exchange, next()).block();

		assertEquals(401, exchange.getResponse().getStatusCode().value());
	}

	@Test
	void validToken_injectsUserHeaders() {
		String token = jwtService.createToken("user-123", "alice@example.com", List.of("CUSTOMER", "ADMIN"));
		MockServerWebExchange exchange = MockServerWebExchange.from(
			MockServerHttpRequest.get("/api/products").header("Authorization", "Bearer " + token));

		AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
		filter.filter(exchange, e -> {
			seen.set(e);
			return Mono.empty();
		}).block();

		assertNotNull(seen.get());
		assertEquals("user-123", seen.get().getRequest().getHeaders().getFirst("X-User-Id"));
		assertEquals("CUSTOMER,ADMIN", seen.get().getRequest().getHeaders().getFirst("X-User-Roles"));
		assertNotNull(seen.get().getRequest().getHeaders().getFirst("X-Request-Id"));
	}

	private GatewayFilterChain next() {
		return exchange -> Mono.empty();
	}
}