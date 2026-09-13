package com.packs.taskservice;

import com.packs.sharedlib.ApiException;
import com.packs.sharedlib.PageResponse;
import com.packs.taskservice.dto.CreateTaskRequest;
import com.packs.taskservice.dto.TaskDto;
import com.packs.taskservice.dto.UpdateTaskRequest;
import com.packs.taskservice.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TaskFlowIntegrationTest {

	@Autowired
	private TaskService taskService;

	private UUID userId;

	@BeforeEach
	void setUp() {
		userId = UUID.randomUUID();
	}

	@Test
	void fullLifecycle_createListFilterUpdateDelete() {
		TaskDto created = taskService.createTask(new CreateTaskRequest(
			"Replenish cold brew stock",
			"Restock shelf by Friday.",
			null,
			"HIGH",
			Instant.now().plus(2, ChronoUnit.DAYS)), userId);

		assertNotNull(created.taskId());
		assertEquals("TODO", created.status());
		assertEquals("HIGH", created.priority());
		assertEquals(userId.toString(), created.userId());

		PageResponse<TaskDto> all = taskService.listTasks(userId, null, 0, 20);
		assertEquals(1, all.total());

		PageResponse<TaskDto> todo = taskService.listTasks(userId, "TODO", 0, 20);
		assertEquals(1, todo.total());
		PageResponse<TaskDto> done = taskService.listTasks(userId, "DONE", 0, 20);
		assertEquals(0, done.total());

		TaskDto updated = taskService.updateTask(
			UUID.fromString(created.taskId()),
			new UpdateTaskRequest(null, "Also order new cups.", "IN_PROGRESS", "LOW", null),
			userId);
		assertEquals("IN_PROGRESS", updated.status());
		assertEquals("LOW", updated.priority());

		TaskDto fetched = taskService.getTask(UUID.fromString(created.taskId()), userId);
		assertEquals("Replenish cold brew stock", fetched.title());

		taskService.deleteTask(UUID.fromString(created.taskId()), userId);
		assertTrue(taskService.listTasks(userId, null, 0, 20).items().isEmpty());
	}

	@Test
	void tasksAreScopedToUser() {
		UUID otherId = UUID.randomUUID();
		TaskDto theirs = taskService.createTask(new CreateTaskRequest("Theirs", null, null, null, null), otherId);

		assertThrows(ApiException.class, () -> taskService.getTask(UUID.fromString(theirs.taskId()), userId));
		assertTrue(taskService.listTasks(userId, null, 0, 20).items().isEmpty());
	}

	@Test
	void updateTask_unknownStatus_rejected() {
		TaskDto created = taskService.createTask(new CreateTaskRequest("Task", null, null, null, null), userId);

		assertThrows(ApiException.class,
			() -> taskService.updateTask(UUID.fromString(created.taskId()),
				new UpdateTaskRequest(null, null, "BOGUS", null, null), userId));
	}

	@Test
	void deleteTask_foreignTask_rejected() {
		UUID otherId = UUID.randomUUID();
		TaskDto theirs = taskService.createTask(new CreateTaskRequest("Theirs", null, null, null, null), otherId);

		assertThrows(ApiException.class,
			() -> taskService.deleteTask(UUID.fromString(theirs.taskId()), userId));
	}
}