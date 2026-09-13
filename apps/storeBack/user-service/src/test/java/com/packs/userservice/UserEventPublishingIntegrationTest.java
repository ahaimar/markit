package com.packs.userservice;

import tools.jackson.databind.ObjectMapper;
import com.packs.sharedlib.UserRegisteredEvent;
import com.packs.userservice.dto.RegisterRequest;
import com.packs.userservice.outbox.UserOutboxPublisher;
import com.packs.userservice.repository.UserOutboxEventRepository;
import com.packs.userservice.service.UserService;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
	"spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
	"app.outbox.publish-interval-ms=86400000"
})
@EmbeddedKafka(topics = "user-events", partitions = 1)
class UserEventPublishingIntegrationTest {

	@Autowired
	private UserService userService;

	@Autowired
	private UserOutboxPublisher userOutboxPublisher;

	@Autowired
	private UserOutboxEventRepository userOutboxEventRepository;

	@Autowired
	private EmbeddedKafkaBroker broker;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void registerUser_enqueuesOutboxAndPublishesEventToKafka() throws Exception {
		userService.register(new RegisterRequest("alice@example.com", "Alice", "Password123"));

		var pending = userOutboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc();
		assertEquals(1, pending.size());
		assertEquals("UserRegistered", pending.get(0).getEventType());

		userOutboxPublisher.publish();

		assertTrue(userOutboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc().isEmpty());

		Map<String, Object> props = KafkaTestUtils.consumerProps("user-events-test", "true", broker);
		Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
			props, new StringDeserializer(), new StringDeserializer()).createConsumer();
		broker.consumeFromAnEmbeddedTopic(consumer, "user-events");
		ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, java.time.Duration.ofSeconds(5));
		consumer.close();

		assertTrue(records.count() >= 1, "Expected at least one UserRegistered event");
		ConsumerRecord<String, String> record = records.iterator().next();
		UserRegisteredEvent event = objectMapper.readValue(record.value(), UserRegisteredEvent.class);
		assertEquals("alice@example.com", event.email());
		assertEquals("Alice", event.name());
	}
}