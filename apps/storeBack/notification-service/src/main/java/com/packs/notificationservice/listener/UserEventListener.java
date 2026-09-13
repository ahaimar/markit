package com.packs.notificationservice.listener;

import tools.jackson.databind.ObjectMapper;
import com.packs.notificationservice.service.NotificationService;
import com.packs.sharedlib.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class UserEventListener {

	private static final Logger log = LoggerFactory.getLogger(UserEventListener.class);

	private final NotificationService notificationService;
	private final ObjectMapper objectMapper;

	public UserEventListener(NotificationService notificationService, ObjectMapper objectMapper) {
		this.notificationService = notificationService;
		this.objectMapper = objectMapper;
	}

	@KafkaListener(topics = "${app.kafka.user-events-topic:user-events}", groupId = "${spring.kafka.consumer.group-id:notification-service}")
	public void onUserEvent(String message) {
		UserRegisteredEvent event = objectMapper.readValue(message, UserRegisteredEvent.class);
		log.info("Consumed UserRegistered event for user {}", event.userId());
		notificationService.onUserRegistered(event);
	}
}