package com.packs.productservice;

import com.packs.productservice.dto.ProductRequest;
import com.packs.productservice.repository.ProductOutboxEventRepository;
import com.packs.productservice.outbox.ProductOutboxPublisher;
import com.packs.productservice.service.ProductService;
import com.packs.sharedlib.ProductDeletedEvent;
import com.packs.sharedlib.ProductUpdatedEvent;
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
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
	"spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
	"app.outbox.publish-interval-ms=86400000"
})
@EmbeddedKafka(topics = "product-events", partitions = 1)
class ProductEventPublishingIntegrationTest {

	@Autowired
	private ProductService productService;

	@Autowired
	private ProductOutboxPublisher productOutboxPublisher;

	@Autowired
	private ProductOutboxEventRepository productOutboxEventRepository;

	@Autowired
	private EmbeddedKafkaBroker broker;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void updateAndDelete_enqueueOutboxAndPublishEventsToKafka() throws Exception {
		productService.create(new ProductRequest("Orig", "desc", new BigDecimal("9.99"), "Mugs", 5));

		String productId = productService.list(0, 5, null, "Orig").items().get(0).id();
		java.util.UUID uuid = java.util.UUID.fromString(productId);

		productService.update(uuid, new ProductRequest("Updated", "desc2", new BigDecimal("19.99"), "Coffee", 8));
		productService.delete(uuid);

		assertEquals(2, productOutboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc().size());

		productOutboxPublisher.publish();

		assertTrue(productOutboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc().isEmpty());

		Map<String, Object> props = KafkaTestUtils.consumerProps("product-events-test", "true", broker);
		Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
			props, new StringDeserializer(), new StringDeserializer()).createConsumer();
		broker.consumeFromAnEmbeddedTopic(consumer, "product-events");
		ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
		consumer.close();

		assertTrue(records.count() >= 2, "Expected ProductUpdated + ProductDeleted events");
		boolean sawUpdated = false;
		boolean sawDeleted = false;
		for (ConsumerRecord<String, String> record : records) {
			String value = record.value();
			if (objectMapper.readTree(value).has("updatedAt")) {
				ProductUpdatedEvent event = objectMapper.readValue(value, ProductUpdatedEvent.class);
				if (event.productId().equals(productId)) {
					sawUpdated = true;
				}
			} else {
				ProductDeletedEvent event = objectMapper.readValue(value, ProductDeletedEvent.class);
				if (event.productId().equals(productId)) {
					sawDeleted = true;
				}
			}
		}
		assertTrue(sawUpdated, "Expected ProductUpdated event on the topic");
		assertTrue(sawDeleted, "Expected ProductDeleted event on the topic");
	}
}