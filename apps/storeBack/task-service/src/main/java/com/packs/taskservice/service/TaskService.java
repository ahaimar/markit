package com.packs.taskservice.service;

import com.packs.sharedlib.ApiException;
import com.packs.sharedlib.PageResponse;
import com.packs.taskservice.dto.CreateTaskRequest;
import com.packs.taskservice.dto.TaskDto;
import com.packs.taskservice.dto.UpdateTaskRequest;
import com.packs.taskservice.entity.Task;
import com.packs.taskservice.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class TaskService {

	private static final Logger log = LoggerFactory.getLogger(TaskService.class);

	private static final Set<String> STATUSES = Set.of("TODO", "IN_PROGRESS", "DONE");
	private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH");
	private static final String DEFAULT_STATUS = "TODO";
	private static final String DEFAULT_PRIORITY = "MEDIUM";

	private final TaskRepository taskRepository;

	public TaskService(TaskRepository taskRepository) {
		this.taskRepository = taskRepository;
	}

	@Transactional
	public TaskDto createTask(CreateTaskRequest request, UUID userId) {
		String status = request.status() == null || request.status().isBlank() ? DEFAULT_STATUS : request.status();
		String priority = request.priority() == null || request.priority().isBlank() ? DEFAULT_PRIORITY : request.priority();
		requireValidStatus(status);
		requireValidPriority(priority);

		Instant now = Instant.now();
		Task task = new Task();
		task.setUserId(userId);
		task.setTitle(request.title());
		task.setDescription(request.description());
		task.setStatus(status.toUpperCase());
		task.setPriority(priority.toUpperCase());
		task.setDueDate(request.dueDate());
		task.setCreatedAt(now);
		task.setUpdatedAt(now);
		Task saved = taskRepository.save(task);
		return toDto(saved);
	}

	@Transactional(readOnly = true)
	public PageResponse<TaskDto> listTasks(UUID userId, String status, int page, int pageSize) {
		PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(pageSize, 1), 100));
		Page<Task> tasks;
		if (status == null || status.isBlank()) {
			tasks = taskRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
		} else {
			requireValidStatus(status);
			tasks = taskRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status.toUpperCase(), pageable);
		}
		return PageResponse.of(
			tasks.getContent().stream().map(this::toDto).toList(),
			tasks.getNumber(),
			tasks.getSize(),
			tasks.getTotalElements());
	}

	@Transactional(readOnly = true)
	public TaskDto getTask(UUID taskId, UUID userId) {
		return toDto(getOwnedTask(taskId, userId));
	}

	@Transactional
	public TaskDto updateTask(UUID taskId, UpdateTaskRequest request, UUID userId) {
		Task task = getOwnedTask(taskId, userId);

		if (request.title() != null) {
			if (request.title().isBlank()) {
				throw new ApiException("ERR_INVALID_TITLE", "Title cannot be blank", HttpStatus.BAD_REQUEST);
			}
			task.setTitle(request.title());
		}
		if (request.description() != null) {
			task.setDescription(request.description());
		}
		if (request.status() != null) {
			requireValidStatus(request.status());
			task.setStatus(request.status().toUpperCase());
		}
		if (request.priority() != null) {
			requireValidPriority(request.priority());
			task.setPriority(request.priority().toUpperCase());
		}
		if (request.dueDate() != null) {
			task.setDueDate(request.dueDate());
		}
		task.setUpdatedAt(Instant.now());
		return toDto(taskRepository.save(task));
	}

	@Transactional
	public void deleteTask(UUID taskId, UUID userId) {
		Task task = getOwnedTask(taskId, userId);
		taskRepository.delete(task);
		log.info("Deleted task {} for user {}", taskId, userId);
	}

	private Task getOwnedTask(UUID taskId, UUID userId) {
		return taskRepository.findByIdAndUserId(taskId, userId)
			.orElseThrow(() -> new ApiException("ERR_TASK_NOT_FOUND", "Task not found", HttpStatus.NOT_FOUND));
	}

	private void requireValidStatus(String status) {
		if (!STATUSES.contains(status.toUpperCase())) {
			throw new ApiException("ERR_INVALID_STATUS", "Invalid status value: " + status, HttpStatus.BAD_REQUEST);
		}
	}

	private void requireValidPriority(String priority) {
		if (!PRIORITIES.contains(priority.toUpperCase())) {
			throw new ApiException("ERR_INVALID_PRIORITY", "Invalid priority value: " + priority, HttpStatus.BAD_REQUEST);
		}
	}

	private TaskDto toDto(Task task) {
		return new TaskDto(
			task.getId().toString(),
			task.getUserId().toString(),
			task.getTitle(),
			task.getDescription(),
			task.getStatus(),
			task.getPriority(),
			task.getDueDate(),
			task.getCreatedAt(),
			task.getUpdatedAt());
	}
}