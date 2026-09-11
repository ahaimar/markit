package com.packs.notificationservice.listener;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.packs.notificationservice.service.NotificationService;
import com.packs.sharedlib.OrderCancelledEvent;
import com.packs.sharedlib.OrderCreatedEvent;
import com.packs.sharedlib.OrderStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

	private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

	private final NotificationService notificationService;
	private final ObjectMapper objectMapper;

	public OrderEventListener(NotificationService notificationService, ObjectMapper objectMapper) {
		this.notificationService = notificationService;
		this.objectMapper = objectMapper;
	}

	@KafkaListener(topics = "${app.kafka.order-events-topic:order-events}", groupId = "${spring.kafka.consumer.group-id:notification-service}")
	public void onOrderEvent(String message) {
		try {
			JsonNode node = objectMapper.readTree(message);
			if (node.has("fromStatus")) {
				OrderStatusChangedEvent event = objectMapper.treeToValue(node, OrderStatusChangedEvent.class);
				log.info("Consumed OrderStatusChanged event for order {} to {}", event.orderId(), event.toStatus());
				notificationService.onOrderStatusChanged(event);
			} else if (node.has("reason")) {
				OrderCancelledEvent event = objectMapper.treeToValue(node, OrderCancelledEvent.class);
				log.info("Consumed OrderCancelled event for order {}", event.orderId());
				notificationService.onOrderCancelled(event);
			} else if (node.has("totalPrice")) {
				OrderCreatedEvent event = objectMapper.treeToValue(node, OrderCreatedEvent.class);
				log.info("Consumed OrderCreated event for order {}", event.orderId());
				notificationService.onOrderCreated(event);
			} else {
				log.info("Ignoring unrecognized order event: {}", message);
			}
		} catch (Exception ex) {
			log.error("Failed to process order event message: {}", message, ex);
		}
	}
}