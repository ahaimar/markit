package com.packs.taskservice.entity;

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
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
public class Task {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(nullable = false, length = 20)
	private String status;

	@Column(nullable = false, length = 20)
	private String priority;

	@Column(name = "due_date")
	private Instant dueDate;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}