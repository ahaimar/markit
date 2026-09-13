package com.packs.notificationservice.repository;

import com.packs.notificationservice.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	List<Notification> findByRecipientOrderByCreatedAtDesc(String recipient);

	Optional<Notification> findByEventId(String eventId);

	Page<Notification> findByRecipientOrderByCreatedAtDesc(String recipient, Pageable pageable);

	Page<Notification> findByRecipientAndStatusOrderByCreatedAtDesc(String recipient, String status, Pageable pageable);

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	default Notification saveInNewTransaction(Notification notification) {
		return saveAndFlush(notification);
	}
}