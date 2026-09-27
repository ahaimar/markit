package com.packs.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitGlobalFilter.class);

    private final RedisRateLimiter rateLimiter;
    private final int perIpLimit;
    private final int perUserLimit;
    private final long windowMillis;

    public RateLimitGlobalFilter(
        RedisRateLimiter rateLimiter,
        @Value("${app.rate-limit.per-ip:100}") int perIpLimit,
        @Value("${app.rate-limit.per-user:1000}") int perUserLimit,
        @Value("${app.rate-limit.window-seconds:60}") long windowSeconds) {
        this.rateLimiter = rateLimiter;
        this.perIpLimit = perIpLimit;
        this.perUserLimit = perUserLimit;
        this.windowMillis = windowSeconds * 1000;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String ip = resolveClientIp(request);
        String userId = request.getHeaders().getFirst("X-User-Id");

        Mono<Boolean> ipAllowed = (ip != null)
            ? rateLimiter.isAllowed("ip:" + ip, perIpLimit, windowMillis)
            : Mono.just(true);

        return ipAllowed.flatMap(allowed -> {
            if (!allowed) {
                log.warn("Rate limit exceeded for client IP {}, per-ip limit {}", ip, perIpLimit);
                return rateLimited(exchange.getResponse());
            }

            if (userId != null && !userId.isBlank()) {
                return rateLimiter.isAllowed("user:" + userId, perUserLimit, windowMillis)
                    .flatMap(userAllowed -> {
                        if (!userAllowed) {
                            log.warn("Rate limit exceeded for user {}, per-user limit {}", userId, perUserLimit);
                            return rateLimited(exchange.getResponse());
                        }
                        return chain.filter(exchange);
                    });
            }

            return chain.filter(exchange);
        });
    }

    private String resolveClientIp(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress remote = request.getRemoteAddress();
        return remote != null && remote.getAddress() != null ? remote.getAddress().getHostAddress() : null;
    }

    private Mono<Void> rateLimited(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"code\":\"ERR_RATE_LIMITED\",\"message\":\"Too many requests\",\"details\":null}"
            .getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -99;
    }
}
