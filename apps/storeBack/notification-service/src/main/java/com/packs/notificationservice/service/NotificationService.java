package com.packs.notificationservice.service;

import com.packs.notificationservice.dto.NotificationDto;
import com.packs.notificationservice.entity.Notification;
import com.packs.notificationservice.repository.NotificationRepository;
import com.packs.sharedlib.OrderCancelledEvent;
import com.packs.sharedlib.OrderCreatedEvent;
import com.packs.sharedlib.OrderStatusChangedEvent;
import com.packs.sharedlib.PageResponse;
import com.packs.sharedlib.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class NotificationService {

	private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

	private final NotificationRepository notificationRepository;

	public NotificationService(NotificationRepository notificationRepository) {
		this.notificationRepository = notificationRepository;
	}

	@Transactional
	public NotificationDto onOrderCreated(OrderCreatedEvent event) {
		String subject = "Order " + event.orderId() + " confirmed";
		String message = "We received your order " + event.orderId() + " for $" + event.totalPrice()
			+ " placed at " + DateTimeFormatter.ISO_INSTANT.format(event.createdAt()) + ".";
		return persist(event.eventId(), event.userId(), "EMAIL", subject, message);
	}

	@Transactional
	public NotificationDto onOrderStatusChanged(OrderStatusChangedEvent event) {
		String subject = "Order " + event.orderId() + " is now " + event.toStatus();
		String message = "Your order " + event.orderId() + " changed status from " + event.fromStatus()
			+ " to " + event.toStatus() + " at " + DateTimeFormatter.ISO_INSTANT.format(event.changedAt()) + ".";
		return persist(event.eventId(), event.userId(), "EMAIL", subject, message);
	}

	@Transactional
	public NotificationDto onOrderCancelled(OrderCancelledEvent event) {
		String subject = "Order " + event.orderId() + " cancelled";
		String message = "Your order " + event.orderId() + " was cancelled"
			+ (event.reason() != null && !event.reason().isBlank() ? " (" + event.reason() + ")" : "")
			+ ". Any reserved amount will be refunded.";
		return persist(event.eventId(), event.userId(), "EMAIL", subject, message);
	}

	@Transactional
	public NotificationDto onUserRegistered(UserRegisteredEvent event) {
		String subject = "Welcome to Markit!";
		String message = "Welcome" + (event.name() != null && !event.name().isBlank() ? ", " + event.name() : "")
			+ "! Your account with email " + event.email() + " has been created successfully.";
		return persist(event.eventId(), event.userId(), "EMAIL", subject, message);
	}

	private NotificationDto persist(String eventId, String recipient, String type, String subject, String message) {
		Notification existing = notificationRepository.findByEventId(eventId).orElse(null);
		if (existing != null) {
			log.info("Duplicate event {} already processed, skipping", eventId);
			return NotificationDto.from(existing);
		}

		Notification notification = new Notification();
		notification.setEventId(eventId);
		notification.setRecipient(recipient);
		notification.setType(type);
		notification.setStatus("SENT");
		notification.setSubject(subject);
		notification.setMessage(message);
		notification.setCreatedAt(Instant.now());

		try {
			notificationRepository.saveInNewTransaction(notification);
		} catch (DataIntegrityViolationException ex) {
			Notification raced = notificationRepository.findByEventId(eventId).orElse(null);
			if (raced == null) {
				throw ex;
			}
			log.info("Concurrent duplicate event {} processed, skipping", eventId);
			return NotificationDto.from(raced);
		}

		log.info("Email sent to user {}: {} (simulated)", recipient, subject);
		return NotificationDto.from(notification);
	}

	@Transactional(readOnly = true)
	public PageResponse<NotificationDto> listForUser(String userId, int page, int pageSize, String status) {
		Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(pageSize, 1), 100));

		Page<Notification> notifications;
		if (status != null && !status.isBlank()) {
			notifications = notificationRepository.findByRecipientAndStatusOrderByCreatedAtDesc(userId, status.toUpperCase(), pageable);
		} else {
			notifications = notificationRepository.findByRecipientOrderByCreatedAtDesc(userId, pageable);
		}

		return PageResponse.of(
			notifications.getContent().stream().map(NotificationDto::from).toList(),
			notifications.getNumber(),
			notifications.getSize(),
			notifications.getTotalElements()
		);
	}

	@Transactional(readOnly = true)
	public NotificationDto getById(UUID notificationId) {
		Notification notification = notificationRepository.findById(notificationId)
			.orElseThrow(() -> new com.packs.sharedlib.ApiException("ERR_NOTIFICATION_NOT_FOUND", "Notification not found", org.springframework.http.HttpStatus.NOT_FOUND));
		return NotificationDto.from(notification);
	}
}