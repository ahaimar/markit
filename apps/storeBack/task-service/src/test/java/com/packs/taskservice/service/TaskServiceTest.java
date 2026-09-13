package com.packs.taskservice.service;

import com.packs.sharedlib.ApiException;
import com.packs.sharedlib.PageResponse;
import com.packs.taskservice.dto.CreateTaskRequest;
import com.packs.taskservice.dto.TaskDto;
import com.packs.taskservice.dto.UpdateTaskRequest;
import com.packs.taskservice.entity.Task;
import com.packs.taskservice.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

	@Mock
	private TaskRepository taskRepository;

	private TaskService taskService;

	private UUID userId;
	private UUID taskId;

	@BeforeEach
	void setUp() {
		taskService = new TaskService(taskRepository);
		userId = UUID.randomUUID();
		taskId = UUID.randomUUID();
	}

	private Task task() {
		Task task = new Task();
		task.setId(taskId);
		task.setUserId(userId);
		task.setTitle("Roast a new batch");
		task.setDescription("Single origin Ethiopia, medium roast.");
		task.setStatus("TODO");
		task.setPriority("HIGH");
		task.setDueDate(Instant.now().plusSeconds(86400));
		task.setCreatedAt(Instant.now());
		task.setUpdatedAt(Instant.now());
		return task;
	}

	private CreateTaskRequest createRequest() {
		return new CreateTaskRequest("Roast a new batch", "Single origin Ethiopia, medium roast.", "TODO", "HIGH",
			Instant.now().plusSeconds(86400));
	}

	@Test
	void createTask_defaultsStatusAndPriority() {
		when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
			Task task = invocation.getArgument(0);
			task.setId(taskId);
			return task;
		});

		CreateTaskRequest request = new CreateTaskRequest("Roast a new batch", null, null, null, null);
		TaskDto dto = taskService.createTask(request, userId);

		assertEquals(taskId.toString(), dto.taskId());
		assertEquals(userId.toString(), dto.userId());
		assertEquals("Roast a new batch", dto.title());
		assertEquals("TODO", dto.status());
		assertEquals("MEDIUM", dto.priority());
	}

	@Test
	void createTask_rejectsInvalidStatus() {
		ApiException ex = assertThrows(ApiException.class,
			() -> taskService.createTask(new CreateTaskRequest("Title", null, "BOGUS", null, null), userId));

		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		assertEquals("ERR_INVALID_STATUS", ex.getCode());
		verify(taskRepository, never()).save(any(Task.class));
	}

	@Test
	void createTask_rejectsInvalidPriority() {
		ApiException ex = assertThrows(ApiException.class,
			() -> taskService.createTask(new CreateTaskRequest("Title", null, null, "URGENT", null), userId));

		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		assertEquals("ERR_INVALID_PRIORITY", ex.getCode());
	}

	@Test
	void listTasks_returnsPageForUser() {
		Page<Task> page = new PageImpl<>(List.of(task()));
		when(taskRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(PageRequest.class))).thenReturn(page);

		PageResponse<TaskDto> result = taskService.listTasks(userId, null, 0, 20);

		assertEquals(1, result.items().size());
		assertEquals(taskId.toString(), result.items().get(0).taskId());
		assertTrue(result.total() == 1);
	}

	@Test
	void listTasks_filtersByStatus() {
		when(taskRepository.findByUserIdAndStatusOrderByCreatedAtDesc(eq(userId), eq("IN_PROGRESS"), any(PageRequest.class)))
			.thenReturn(new PageImpl<>(List.of()));

		PageResponse<TaskDto> result = taskService.listTasks(userId, "in_progress", 0, 20);

		assertTrue(result.items().isEmpty());
		verify(taskRepository).findByUserIdAndStatusOrderByCreatedAtDesc(eq(userId), eq("IN_PROGRESS"), any(PageRequest.class));
	}

	@Test
	void getTask_returnsOwnedTask() {
		when(taskRepository.findByIdAndUserId(taskId, userId)).thenReturn(Optional.of(task()));

		TaskDto dto = taskService.getTask(taskId, userId);

		assertEquals(taskId.toString(), dto.taskId());
		assertEquals("TODO", dto.status());
	}

	@Test
	void getTask_foreignTask_throwsNotFound() {
		when(taskRepository.findByIdAndUserId(taskId, userId)).thenReturn(Optional.empty());

		ApiException ex = assertThrows(ApiException.class, () -> taskService.getTask(taskId, userId));

		assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
		assertEquals("ERR_TASK_NOT_FOUND", ex.getCode());
	}

	@Test
	void updateTask_updatesFields() {
		when(taskRepository.findByIdAndUserId(taskId, userId)).thenReturn(Optional.of(task()));
		when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

		TaskDto dto = taskService.updateTask(taskId, new UpdateTaskRequest("New title", "New description", "DONE", "LOW", null), userId);

		assertEquals("New title", dto.title());
		assertEquals("New description", dto.description());
		assertEquals("DONE", dto.status());
		assertEquals("LOW", dto.priority());
	}

	@Test
	void updateTask_blankTitle_throwsBadRequest() {
		when(taskRepository.findByIdAndUserId(taskId, userId)).thenReturn(Optional.of(task()));

		ApiException ex = assertThrows(ApiException.class,
			() -> taskService.updateTask(taskId, new UpdateTaskRequest("   ", null, null, null, null), userId));

		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		assertEquals("ERR_INVALID_TITLE", ex.getCode());
	}

	@Test
	void updateTask_partialUpdatePreservesOtherFields() {
		when(taskRepository.findByIdAndUserId(taskId, userId)).thenReturn(Optional.of(task()));
		when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

		TaskDto dto = taskService.updateTask(taskId, new UpdateTaskRequest(null, null, "IN_PROGRESS", null, null), userId);

		assertEquals("IN_PROGRESS", dto.status());
		assertEquals("Roast a new batch", dto.title());
		assertEquals("HIGH", dto.priority());
	}

	@Test
	void deleteTask_removesOwnedTask() {
		Task owned = task();
		when(taskRepository.findByIdAndUserId(taskId, userId)).thenReturn(Optional.of(owned));

		taskService.deleteTask(taskId, userId);

		verify(taskRepository).delete(owned);
	}

	@Test
	void deleteTask_foreignTask_throwsNotFound() {
		when(taskRepository.findByIdAndUserId(taskId, userId)).thenReturn(Optional.empty());

		assertThrows(ApiException.class, () -> taskService.deleteTask(taskId, userId));

		verify(taskRepository, never()).delete(any(Task.class));
	}
}