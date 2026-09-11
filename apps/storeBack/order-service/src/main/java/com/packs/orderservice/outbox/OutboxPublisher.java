package com.packs.orderservice.outbox;

import com.packs.orderservice.entity.OutboxEvent;
import com.packs.orderservice.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboxPublisher {

	private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

	private final OutboxEventRepository outboxEventRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final String topic;

	public OutboxPublisher(
		OutboxEventRepository outboxEventRepository,
		KafkaTemplate<String, String> kafkaTemplate,
		@Value("${app.kafka.order-events-topic:order-events}") String topic) {
		this.outboxEventRepository = outboxEventRepository;
		this.kafkaTemplate = kafkaTemplate;
		this.topic = topic;
	}

	@Scheduled(fixedDelayString = "${app.outbox.publish-interval-ms:3000}")
	public void publish() {
		List<OutboxEvent> events = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc();
		for (OutboxEvent event : events) {
			try {
				kafkaTemplate.send(topic, event.getPayload()).get();
				outboxEventRepository.markPublished(event.getId(), java.time.Instant.now());
				log.info("Published outbox event id={} type={}", event.getId(), event.getEventType());
			} catch (Exception ex) {
				log.warn("Failed to publish outbox event id={}, will retry: {}", event.getId(), ex.getMessage());
				break;
			}
		}
	}
}