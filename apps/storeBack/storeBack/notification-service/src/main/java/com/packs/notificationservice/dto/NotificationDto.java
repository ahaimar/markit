package com.packs.notificationservice.dto;

import com.packs.notificationservice.entity.Notification;

import java.time.Instant;

public record NotificationDto(
	String id,
	String eventId,
	String recipient,
	String type,
	String status,
	String subject,
	String message,
	Instant createdAt
) {

	public static NotificationDto from(Notification n) {
		return new NotificationDto(
			n.getId().toString(),
			n.getEventId(),
			n.getRecipient(),
			n.getType(),
			n.getStatus(),
			n.getSubject(),
			n.getMessage(),
			n.getCreatedAt()
		);
	}
}