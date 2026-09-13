package com.packs.notificationservice.service;

import com.packs.notificationservice.entity.Notification;
import com.packs.notificationservice.repository.NotificationRepository;
import com.packs.sharedlib.OrderCancelledEvent;
import com.packs.sharedlib.OrderCreatedEvent;
import com.packs.sharedlib.OrderStatusChangedEvent;
import com.packs.sharedlib.UserRegisteredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	@Mock
	private NotificationRepository notificationRepository;

	private NotificationService notificationService;

	private UUID userId;

	@BeforeEach
	void setUp() {
		notificationService = new NotificationService(notificationRepository);
		userId = UUID.randomUUID();
	}

	private void stubSave() {
		when(notificationRepository.saveInNewTransaction(any(Notification.class))).thenAnswer(invocation -> {
			Notification n = invocation.getArgument(0);
			n.setId(UUID.randomUUID());
			return n;
		});
	}

	@Test
	void onOrderCreated_persistsEmailNotification() {
		stubSave();
		var dto = notificationService.onOrderCreated(
			new OrderCreatedEvent(UUID.randomUUID().toString(), UUID.randomUUID().toString(), userId.toString(), new BigDecimal("37.00"), Instant.now()));

		assertEquals("EMAIL", dto.type());
		assertEquals("SENT", dto.status());
		assertEquals(userId.toString(), dto.recipient());
		assertTrue(dto.subject().contains("confirmed"));
		verify(notificationRepository).saveInNewTransaction(any(Notification.class));
	}

	@Test
	void onOrderStatusChanged_subjectCarriesNewStatus() {
		stubSave();
		var dto = notificationService.onOrderStatusChanged(
			new OrderStatusChangedEvent(UUID.randomUUID().toString(), UUID.randomUUID().toString(), userId.toString(), new BigDecimal("37.00"), "CONFIRMED", "SHIPPED", Instant.now()));

		assertEquals("SENT", dto.status());
		assertTrue(dto.subject().contains("SHIPPED"));
		assertTrue(dto.message().contains("CONFIRMED"));
	}

	@Test
	void onOrderCancelled_subjectMarksCancellation() {
		stubSave();
		var dto = notificationService.onOrderCancelled(
			new OrderCancelledEvent(UUID.randomUUID().toString(), UUID.randomUUID().toString(), userId.toString(), new BigDecimal("37.00"), "Cancelled by admin", Instant.now()));

		assertTrue(dto.subject().contains("cancelled"));
		assertTrue(dto.message().contains("Cancelled by admin"));
	}

	@Test
	void onUserRegistered_welcomeEmailSent() {
		stubSave();
		var dto = notificationService.onUserRegistered(
			new UserRegisteredEvent(UUID.randomUUID().toString(), userId.toString(), "user@example.com", "Alice", Instant.now()));

		assertEquals(userId.toString(), dto.recipient());
		assertEquals("EMAIL", dto.type());
		assertTrue(dto.subject().contains("Welcome"));
		assertTrue(dto.message().contains("Alice"));
		assertTrue(dto.message().contains("user@example.com"));
	}

	@Test
	void duplicateEventId_skipsPersistAndReturnsExisting() {
		Notification existing = new Notification();
		existing.setId(UUID.randomUUID());
		existing.setEventId("evt-123");
		when(notificationRepository.findByEventId("evt-123")).thenReturn(java.util.Optional.of(existing));

		var dto = notificationService.onOrderCreated(
			new OrderCreatedEvent("evt-123", UUID.randomUUID().toString(), userId.toString(), new BigDecimal("37.00"), Instant.now()));

		assertEquals(existing.getId().toString(), dto.id());
		verify(notificationRepository, org.mockito.Mockito.never()).saveInNewTransaction(any(Notification.class));
	}
}
