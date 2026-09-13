package com.packs.userservice.outbox;

import com.packs.userservice.entity.UserOutboxEvent;
import com.packs.userservice.repository.UserOutboxEventRepository;
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
public class UserOutboxPublisher {

	private static final Logger log = LoggerFactory.getLogger(UserOutboxPublisher.class);

	private final UserOutboxEventRepository userOutboxEventRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final String userEventsTopic;

	public UserOutboxPublisher(
		UserOutboxEventRepository userOutboxEventRepository,
		KafkaTemplate<String, String> kafkaTemplate,
		@Value("${app.kafka.user-events-topic:user-events}") String userEventsTopic) {
		this.userOutboxEventRepository = userOutboxEventRepository;
		this.kafkaTemplate = kafkaTemplate;
		this.userEventsTopic = userEventsTopic;
	}

	@Scheduled(fixedDelayString = "${app.outbox.publish-interval-ms:3000}")
	public void publish() {
		List<UserOutboxEvent> events = userOutboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc();
		for (UserOutboxEvent event : events) {
			try {
				kafkaTemplate.send(userEventsTopic, event.getPayload()).get(5, TimeUnit.SECONDS);
				userOutboxEventRepository.markPublished(event.getId(), Instant.now());
				log.info("Published outbox event id={} type={}", event.getId(), event.getEventType());
			} catch (Exception ex) {
				log.warn("Failed to publish outbox event id={}, will retry: {}", event.getId(), ex.getMessage());
				break;
			}
		}
	}
}