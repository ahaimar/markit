package com.packs.taskservice.controller;

import com.packs.sharedlib.PageResponse;
import com.packs.taskservice.dto.CreateTaskRequest;
import com.packs.taskservice.dto.TaskDto;
import com.packs.taskservice.dto.UpdateTaskRequest;
import com.packs.taskservice.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

	private final TaskService taskService;

	public TaskController(TaskService taskService) {
		this.taskService = taskService;
	}

	@GetMapping
	public ResponseEntity<PageResponse<TaskDto>> listTasks(
		@RequestHeader("X-User-Id") UUID userId,
		@RequestParam(required = false) String status,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int pageSize) {
		return ResponseEntity.ok(taskService.listTasks(userId, status, page, pageSize));
	}

	@PostMapping
	public ResponseEntity<TaskDto> createTask(
		@RequestHeader("X-User-Id") UUID userId,
		@Valid @RequestBody CreateTaskRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(taskService.createTask(request, userId));
	}

	@GetMapping("/{taskId}")
	public ResponseEntity<TaskDto> getTask(
		@RequestHeader("X-User-Id") UUID userId,
		@PathVariable UUID taskId) {
		return ResponseEntity.ok(taskService.getTask(taskId, userId));
	}

	@PutMapping("/{taskId}")
	public ResponseEntity<TaskDto> updateTask(
		@RequestHeader("X-User-Id") UUID userId,
		@PathVariable UUID taskId,
		@Valid @RequestBody UpdateTaskRequest request) {
		return ResponseEntity.ok(taskService.updateTask(taskId, request, userId));
	}

	@PatchMapping("/{taskId}/status")
	public ResponseEntity<TaskDto> updateTaskStatus(
		@RequestHeader("X-User-Id") UUID userId,
		@PathVariable UUID taskId,
		@RequestBody UpdateStatusRequest request) {
		return ResponseEntity.ok(taskService.updateTask(taskId, new UpdateTaskRequest(null, null, request.status(), null, null), userId));
	}

	@DeleteMapping("/{taskId}")
	public ResponseEntity<Void> deleteTask(
		@RequestHeader("X-User-Id") UUID userId,
		@PathVariable UUID taskId) {
		taskService.deleteTask(taskId, userId);
		return ResponseEntity.noContent().build();
	}

	public record UpdateStatusRequest(String status) {
	}
}