package com.packs.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Component
public class RedisRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<Long> script;

    public RedisRateLimiter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = RedisScript.of(
            "local key = KEYS[1] " +
            "local now = tonumber(ARGV[1]) " +
            "local window = tonumber(ARGV[2]) " +
            "local limit = tonumber(ARGV[3]) " +
            "redis.call('ZREMRANGEBYSCORE', key, 0, now - window) " +
            "local count = redis.call('ZCARD', key) " +
            "if count < limit then " +
            "  redis.call('ZADD', key, now, now) " +
            "  redis.call('PEXPIRE', key, window) " +
            "  return 1 " +
            "else " +
            "  return 0 " +
            "end", Long.class);
    }

    public Mono<Boolean> isAllowed(String key, int limit, long windowMillis) {
        long now = Instant.now().toEpochMilli();
        return redisTemplate.execute(script, List.of("rate_limit:" + key), String.valueOf(now), String.valueOf(windowMillis), String.valueOf(limit))
            .next()
            .map(result -> result != null && result == 1L)
            .onErrorResume(ex -> {
                log.warn("Redis rate limiter error for key {}: {}. Allowing request.", key, ex.getMessage());
                return Mono.just(true);
            });
    }
}
