package com.packs.userservice.event;

import com.packs.sharedlib.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

@Component
public class UserEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

	private final KafkaTemplate<String, String> kafkaTemplate;
	private final ObjectMapper objectMapper;
	private final String userEventsTopic;

	public UserEventPublisher(
		KafkaTemplate<String, String> kafkaTemplate,
		ObjectMapper objectMapper,
		@Value("${app.kafka.user-events-topic:user-events}") String userEventsTopic) {
		this.kafkaTemplate = kafkaTemplate;
		this.objectMapper = objectMapper;
		this.userEventsTopic = userEventsTopic;
	}

	public void userRegistered(UUID userId, String email, String name) {
		UserRegisteredEvent event = new UserRegisteredEvent(userId.toString(), email, name, Instant.now());
		try {
			String payload = objectMapper.writeValueAsString(event);
			kafkaTemplate.send(userEventsTopic, userId.toString(), payload);
			log.info("Published UserRegistered event for user {}", userId);
		} catch (JacksonException ex) {
			log.warn("Failed to serialize UserRegistered event for user {}: {}", userId, ex.getMessage());
		} catch (Exception ex) {
			log.warn("Failed to publish UserRegistered event for user {}: {}", userId, ex.getMessage());
		}
	}
}