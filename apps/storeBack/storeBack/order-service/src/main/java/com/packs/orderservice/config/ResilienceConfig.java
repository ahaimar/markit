package com.packs.orderservice.config;

import feign.FeignException;
import feign.Retryer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienceConfig {

	@Bean
	public Retryer feignRetryer() {
		return new Retryer.Default(100L, 200L, 3);
	}

	@Bean
	public Customizer<Resilience4JCircuitBreakerFactory> productServiceCircuitBreakerCustomizer() {
		return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
			.circuitBreakerConfig(CircuitBreakerConfig.custom()
				.slidingWindowSize(10)
				.minimumNumberOfCalls(5)
				.failureRateThreshold(50)
				.waitDurationInOpenState(Duration.ofSeconds(30))
				.permittedNumberOfCallsInHalfOpenState(3)
				.ignoreExceptions(FeignException.NotFound.class, FeignException.Conflict.class)
				.build())
			.timeLimiterConfig(TimeLimiterConfig.custom()
				.timeoutDuration(Duration.ofSeconds(5))
				.build())
			.build());
	}
}