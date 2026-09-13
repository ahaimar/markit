package com.packs.taskservice.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateTaskRequest(
	@Size(max = 200, message = "Title must be at most 200 characters")
	String title,
	@Size(max = 5000, message = "Description must be at most 5000 characters")
	String description,
	String status,
	String priority,
	Instant dueDate
) {
}