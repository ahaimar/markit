package com.packs.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class GatewayRouteConfigurationTest {

	@Autowired
	private RouteDefinitionLocator routeDefinitionLocator;

	@Test
	void routeDefinitions_loadedFromConfig() {
		List<String> routeIds = routeDefinitionLocator.getRouteDefinitions()
			.map(route -> route.getId())
			.collectList()
			.block();

		assertEquals(List.of(
			"user-service",
			"product-service",
			"order-service",
			"notification-service",
			"task-service"), routeIds);
	}
}