package com.packs.notificationservice.repository;

import com.packs.notificationservice.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	List<Notification> findByRecipientOrderByCreatedAtDesc(String recipient);

	Page<Notification> findByRecipientOrderByCreatedAtDesc(String recipient, Pageable pageable);

	Page<Notification> findByRecipientAndStatusOrderByCreatedAtDesc(String recipient, String status, Pageable pageable);
}