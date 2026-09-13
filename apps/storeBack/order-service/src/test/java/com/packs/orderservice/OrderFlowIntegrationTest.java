package com.packs.orderservice;

import tools.jackson.databind.ObjectMapper;
import com.packs.orderservice.client.ProductClient;
import com.packs.orderservice.dto.OrderDto;
import com.packs.orderservice.dto.PlaceOrderRequest;
import com.packs.orderservice.entity.Cart;
import com.packs.orderservice.entity.CartItem;
import com.packs.orderservice.entity.OutboxEvent;
import com.packs.orderservice.outbox.OutboxPublisher;
import com.packs.orderservice.repository.CartItemRepository;
import com.packs.orderservice.repository.CartRepository;
import com.packs.orderservice.repository.OutboxEventRepository;
import com.packs.orderservice.repository.OrderRepository;
import com.packs.orderservice.service.OrderService;
import com.packs.sharedlib.OrderCreatedEvent;
import com.packs.sharedlib.ProductDto;
import com.packs.sharedlib.StockDecrementRequest;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
	"spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
	"app.outbox.publish-interval-ms=86400000"
})
@EmbeddedKafka(topics = "order-events", partitions = 1)
class OrderFlowIntegrationTest {

	@Autowired
	private OrderService orderService;

	@Autowired
	private OutboxPublisher outboxPublisher;

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Autowired
	private CartRepository cartRepository;

	@Autowired
	private CartItemRepository cartItemRepository;

	@Autowired
	private EmbeddedKafkaBroker broker;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private ProductClient productClient;

	@Test
	void placeOrderPersistsOutboxAndPublishesEventToKafka() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID productId = UUID.randomUUID();

		when(productClient.getProduct(anyString()))
			.thenReturn(new ProductDto(productId.toString(), "Ceramic Mug", "Stoneware", new BigDecimal("18.50"), "Mugs", 50));
		doNothing().when(productClient).decrementStock(anyString(), any(StockDecrementRequest.class));

		Cart cart = new Cart();
		cart.setUserId(userId);
		cart.setCreatedAt(Instant.now());
		cart.setUpdatedAt(Instant.now());
		cart = cartRepository.save(cart);

		CartItem item = new CartItem();
		item.setCartId(cart.getId());
		item.setProductId(productId);
		item.setQuantity(2);
		item.setCreatedAt(Instant.now());
		cartItemRepository.save(item);

		OrderDto order = orderService.placeOrder(new PlaceOrderRequest(cart.getId(), "1 Main St"), userId, null);

		assertEquals("PENDING", order.status());
		assertEquals(0, new BigDecimal("37.00").compareTo(order.totalPrice()));

		var pending = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc();
		assertEquals(1, pending.size());
		assertEquals("OrderCreated", pending.get(0).getEventType());

		outboxPublisher.publish();

		assertTrue(outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc().isEmpty());

		Map<String, Object> props = KafkaTestUtils.consumerProps("test-group", "true", broker);
		Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
			props, new StringDeserializer(), new StringDeserializer()).createConsumer();
		broker.consumeFromAnEmbeddedTopic(consumer, "order-events");
		ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, java.time.Duration.ofSeconds(5));
		consumer.close();

		assertTrue(records.count() >= 1);
		ConsumerRecord<String, String> record = records.iterator().next();
		OrderCreatedEvent event = objectMapper.readValue(record.value(), OrderCreatedEvent.class);
		assertEquals(order.orderId(), event.orderId());
		assertEquals(userId.toString(), event.userId());
	}
}