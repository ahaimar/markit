package com.packs.notificationservice;

import tools.jackson.databind.ObjectMapper;
import com.packs.notificationservice.repository.NotificationRepository;
import com.packs.sharedlib.OrderCreatedEvent;
import com.packs.sharedlib.OrderStatusChangedEvent;
import com.packs.sharedlib.UserRegisteredEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
	"spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@EmbeddedKafka(topics = { "order-events", "user-events", "order-events-dlq", "user-events-dlq" }, partitions = 1)
class OrderEventListenerIntegrationTest {

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private EmbeddedKafkaBroker broker;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void consumesOrderCreatedEventAndPersistsNotification() throws Exception {
		UUID orderId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		OrderCreatedEvent event = new OrderCreatedEvent(UUID.randomUUID().toString(), orderId.toString(), userId.toString(), new BigDecimal("37.00"), Instant.now());

		send("order-events", objectMapper.writeValueAsString(event));

		List<com.packs.notificationservice.entity.Notification> rows = await(userId.toString());
		assertTrue(rows.size() == 1, "Expected exactly one notification row, got " + rows.size());
		assertEquals("EMAIL", rows.get(0).getType());
		assertEquals("SENT", rows.get(0).getStatus());
		assertEquals(event.eventId(), rows.get(0).getEventId());
	}

	@Test
	void consumesOrderStatusChangedEventAndPersistsNotification() throws Exception {
		UUID orderId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		OrderStatusChangedEvent event = new OrderStatusChangedEvent(
			UUID.randomUUID().toString(), orderId.toString(), userId.toString(), new BigDecimal("37.00"), "CONFIRMED", "SHIPPED", Instant.now());

		send("order-events", objectMapper.writeValueAsString(event));

		List<com.packs.notificationservice.entity.Notification> rows = await(userId.toString());
		assertTrue(rows.size() == 1, "Expected exactly one notification row, got " + rows.size());
		assertEquals(event.eventId(), rows.get(0).getEventId());
		assertTrue(rows.get(0).getSubject().contains("SHIPPED"));
	}

	@Test
	void consumesUserRegisteredEventAndPersistsNotification() throws Exception {
		UUID userId = UUID.randomUUID();
		UserRegisteredEvent event = new UserRegisteredEvent(UUID.randomUUID().toString(), userId.toString(), "alice@example.com", "Alice", Instant.now());

		send("user-events", objectMapper.writeValueAsString(event));

		List<com.packs.notificationservice.entity.Notification> rows = await(userId.toString());
		assertTrue(rows.size() == 1, "Expected exactly one notification row, got " + rows.size());
		assertEquals("EMAIL", rows.get(0).getType());
		assertEquals(userId.toString(), rows.get(0).getRecipient());
		assertTrue(rows.get(0).getSubject().contains("Welcome"));
	}

	@Test
	void duplicateEventId_doesNotCreateSecondNotification() throws Exception {
		UUID userId = UUID.randomUUID();
		OrderCreatedEvent event = new OrderCreatedEvent(UUID.randomUUID().toString(), UUID.randomUUID().toString(), userId.toString(), new BigDecimal("37.00"), Instant.now());

		send("order-events", objectMapper.writeValueAsString(event));
		await(userId.toString());

		send("order-events", objectMapper.writeValueAsString(event));
		java.util.concurrent.TimeUnit.MILLISECONDS.sleep(1000);

		List<com.packs.notificationservice.entity.Notification> rows = notificationRepository.findByRecipientOrderByCreatedAtDesc(userId.toString());
		assertEquals(1, rows.size(), "Expected a single notification row for a duplicated event, got " + rows.size());
	}

	@Test
	void poisonMessage_isRoutedToDlq() throws Exception {
		send("order-events", "{not-valid-json");

		Map<String, Object> props = KafkaTestUtils.consumerProps("dlq-test-group", "true", broker);
		Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
			props, new StringDeserializer(), new StringDeserializer()).createConsumer();
		broker.consumeFromAnEmbeddedTopic(consumer, "order-events-dlq");
		ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, java.time.Duration.ofSeconds(20));
		consumer.close();

		assertTrue(records.count() >= 1, "Expected the poison message to be routed to the DLQ");
	}

	private void send(String topic, String payload) throws Exception {
		Map<String, Object> producerProps = KafkaTestUtils.producerProps(broker);
		producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		DefaultKafkaProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(
			producerProps, new StringSerializer(), new StringSerializer());
		KafkaTemplate<String, String> template = new KafkaTemplate<>(factory);
		template.send(topic, payload).get();
	}

	private List<com.packs.notificationservice.entity.Notification> await(String recipient) throws InterruptedException {
		List<com.packs.notificationservice.entity.Notification> rows = List.of();
		for (int i = 0; i < 50; i++) {
			rows = notificationRepository.findByRecipientOrderByCreatedAtDesc(recipient);
			if (rows.size() >= 1) {
				break;
			}
			Thread.sleep(100);
		}
		return rows;
	}
}