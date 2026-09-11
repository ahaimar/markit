package com.packs.notificationservice.controller;

import com.packs.notificationservice.dto.NotificationDto;
import com.packs.notificationservice.service.NotificationService;
import com.packs.sharedlib.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@GetMapping
	public PageResponse<NotificationDto> list(
		@RequestHeader("X-User-Id") String userId,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int pageSize,
		@RequestParam(required = false) String status
	) {
		return notificationService.listForUser(userId, page, pageSize, status);
	}

	@GetMapping("/{notifId}")
	public ResponseEntity<NotificationDto> getById(@PathVariable UUID notifId) {
		return ResponseEntity.ok(notificationService.getById(notifId));
	}
}