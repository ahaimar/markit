package com.packs.apigateway.filter;

import com.packs.sharedlib.JwtService;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

	private static final List<String> PUBLIC_PATHS = List.of(
		"/api/users/register",
		"/api/users/login",
		"/api/users/refresh",
		"/auth/oauth/google",
		"/auth/oauth/google/callback"
	);

	public static final String REQUEST_ID_HEADER = "X-Request-Id";

	private final JwtService jwtService;
	private final AntPathMatcher pathMatcher = new AntPathMatcher();

	public JwtAuthGlobalFilter(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		ServerHttpRequest withRequestId = exchange.getRequest().mutate()
			.header(REQUEST_ID_HEADER, resolveRequestId(exchange))
			.build();
		ServerWebExchange current = exchange.mutate().request(withRequestId).build();

		String path = current.getRequest().getURI().getPath();
		if (isPublic(path)) {
			return chain.filter(current);
		}

		String header = current.getRequest().getHeaders().getFirst("Authorization");
		if (header == null || !header.startsWith("Bearer ")) {
			return unauthorized(current.getResponse(), "ERR_UNAUTHORIZED", "Missing or malformed Authorization header");
		}

		String token = header.substring(7);
		if (!jwtService.isValid(token)) {
			return unauthorized(current.getResponse(), "ERR_UNAUTHORIZED", "Invalid or expired access token");
		}

		String userId = jwtService.parseUserId(token);
		String roles = String.join(",", jwtService.parseRoles(token));

		ServerHttpRequest withIdentity = current.getRequest().mutate()
			.header("X-User-Id", userId)
			.header("X-User-Roles", roles)
			.build();

		return chain.filter(current.mutate().request(withIdentity).build());
	}

	private String resolveRequestId(ServerWebExchange exchange) {
		String incoming = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
		String requestId = (incoming != null && !incoming.isBlank()) ? incoming : java.util.UUID.randomUUID().toString();
		exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
		return requestId;
	}

	private boolean isPublic(String path) {
		return PUBLIC_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
	}

	private Mono<Void> unauthorized(ServerHttpResponse response, String code, String message) {
		response.setStatusCode(HttpStatus.UNAUTHORIZED);
		response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		byte[] body = ("{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"details\":null}")
			.getBytes(StandardCharsets.UTF_8);
		DataBuffer buffer = response.bufferFactory().wrap(body);
		return response.writeWith(Mono.just(buffer));
	}

	@Override
	public int getOrder() {
		return -100;
	}
}