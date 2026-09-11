package com.packs.orderservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records", uniqueConstraints = {
	@UniqueConstraint(name = "uq_idempotency_user_key", columnNames = { "user_id", "idempotency_key" })
})
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "idempotency_key", nullable = false)
	private String idempotencyKey;

	@Column(name = "request_hash", nullable = false)
	private String requestHash;

	@Column(name = "response_payload", nullable = false, columnDefinition = "TEXT")
	private String responsePayload;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
}