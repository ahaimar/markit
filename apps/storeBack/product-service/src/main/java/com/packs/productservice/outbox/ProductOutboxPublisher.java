package com.packs.productservice.outbox;

import com.packs.productservice.entity.ProductOutboxEvent;
import com.packs.productservice.repository.ProductOutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class ProductOutboxPublisher {

	private static final Logger log = LoggerFactory.getLogger(ProductOutboxPublisher.class);

	private final ProductOutboxEventRepository productOutboxEventRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final String productEventsTopic;

	public ProductOutboxPublisher(
		ProductOutboxEventRepository productOutboxEventRepository,
		KafkaTemplate<String, String> kafkaTemplate,
		@Value("${app.kafka.product-events-topic:product-events}") String productEventsTopic) {
		this.productOutboxEventRepository = productOutboxEventRepository;
		this.kafkaTemplate = kafkaTemplate;
		this.productEventsTopic = productEventsTopic;
	}

	@Scheduled(fixedDelayString = "${app.outbox.publish-interval-ms:3000}")
	public void publish() {
		List<ProductOutboxEvent> events = productOutboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc();
		for (ProductOutboxEvent event : events) {
			try {
				kafkaTemplate.send(productEventsTopic, event.getPayload()).get(5, TimeUnit.SECONDS);
				productOutboxEventRepository.markPublished(event.getId(), Instant.now());
				log.info("Published outbox event id={} type={}", event.getId(), event.getEventType());
			} catch (Exception ex) {
				log.warn("Failed to publish outbox event id={}, will retry: {}", event.getId(), ex.getMessage());
				break;
			}
		}
	}
}