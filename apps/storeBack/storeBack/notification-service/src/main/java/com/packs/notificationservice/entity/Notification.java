package com.packs.notificationservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "event_id")
	private String eventId;

	@Column(nullable = false)
	private String recipient;

	@Column(nullable = false)
	private String type;

	@Column(nullable = false)
	private String status;

	private String subject;

	@Column(columnDefinition = "TEXT")
	private String message;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
}