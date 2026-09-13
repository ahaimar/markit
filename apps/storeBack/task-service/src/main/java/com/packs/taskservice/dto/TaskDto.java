package com.packs.taskservice.dto;

import java.time.Instant;

public record TaskDto(
	String taskId,
	String userId,
	String title,
	String description,
	String status,
	String priority,
	Instant dueDate,
	Instant createdAt,
	Instant updatedAt
) {
}