package com.packs.apigateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitFilterTest {

	@Test
	void perIpLimitReturns429AfterLimit() {
		RateLimitGlobalFilter filter = new RateLimitGlobalFilter(3, 100, 60);
		AtomicInteger forwarded = new AtomicInteger();

		for (int i = 1; i <= 3; i++) {
			MockServerWebExchange exchange = exchangeForIp("203.0.113.10");
			filter.filter(exchange, e -> {
				forwarded.incrementAndGet();
				return Mono.empty();
			}).block();
		}

		assertEquals(3, forwarded.get(), "first three requests should pass");

		MockServerWebExchange exceeded = exchangeForIp("203.0.113.10");
		filter.filter(exceeded, next()).block();

		assertEquals(429, exceeded.getResponse().getStatusCode().value());
		assertEquals(3, forwarded.get(), "the blocked request must not reach the chain");
	}

	@Test
	void perUserLimitAppliesOnTopOfIpLimit() {
		RateLimitGlobalFilter filter = new RateLimitGlobalFilter(100, 2, 60);
		AtomicInteger forwarded = new AtomicInteger();

		MockServerWebExchange first = exchangeForUser("198.51.100.20", "user-7");
		MockServerWebExchange second = exchangeForUser("198.51.100.20", "user-7");
		MockServerWebExchange third = exchangeForUser("198.51.100.20", "user-7");

		filter.filter(first, e -> {
			forwarded.incrementAndGet();
			return Mono.empty();
		}).block();
		filter.filter(second, e -> {
			forwarded.incrementAndGet();
			return Mono.empty();
		}).block();
		filter.filter(third, next()).block();

		assertEquals(2, forwarded.get(), "first two requests should pass");
		assertEquals(429, third.getResponse().getStatusCode().value());
	}

	@Test
	void differentIpsAreCountedSeparately() {
		RateLimitGlobalFilter filter = new RateLimitGlobalFilter(2, 100, 60);
		AtomicInteger forwarded = new AtomicInteger();

		MockServerWebExchange a1 = exchangeForIp("10.0.0.1");
		MockServerWebExchange a2 = exchangeForIp("10.0.0.1");
		MockServerWebExchange b1 = exchangeForIp("10.0.0.2");

		filter.filter(a1, e -> {
			forwarded.incrementAndGet();
			return Mono.empty();
		}).block();
		filter.filter(a2, e -> {
			forwarded.incrementAndGet();
			return Mono.empty();
		}).block();
		filter.filter(b1, e -> {
			forwarded.incrementAndGet();
			return Mono.empty();
		}).block();

		assertEquals(3, forwarded.get(), "two distinct IPs should not share limits");
	}

	private MockServerWebExchange exchangeForIp(String ip) {
		return MockServerWebExchange.from(MockServerHttpRequest.get("/api/products")
			.remoteAddress(new InetSocketAddress(ip, 0))
			.build());
	}

	private MockServerWebExchange exchangeForUser(String ip, String userId) {
		return MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders")
			.remoteAddress(new InetSocketAddress(ip, 0))
			.header("X-User-Id", userId)
			.build());
	}

	private GatewayFilterChain next() {
		return exchange -> Mono.empty();
	}
}