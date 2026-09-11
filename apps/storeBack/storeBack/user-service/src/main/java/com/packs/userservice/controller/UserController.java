package com.packs.userservice.controller;

import com.packs.sharedlib.ApiException;
import com.packs.userservice.dto.ChangePasswordRequest;
import com.packs.userservice.dto.UpdateProfileRequest;
import com.packs.userservice.dto.UserResponse;
import com.packs.userservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/{userId}")
	public UserResponse getUser(@PathVariable UUID userId) {
		requireOwnerOrAdmin(userId);
		return userService.getById(userId);
	}

	@PutMapping("/{userId}")
	public UserResponse updateProfile(@PathVariable UUID userId, @Valid @RequestBody UpdateProfileRequest request) {
		requireOwnerOrAdmin(userId);
		return userService.updateProfile(userId, request);
	}

	@DeleteMapping("/{userId}")
	public ResponseEntity<Void> deleteUser(@PathVariable UUID userId) {
		requireOwnerOrAdmin(userId);
		userService.softDelete(userId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/change-password")
	public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
		userService.changePassword(currentUserId(), request);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/admin/all")
	@PreAuthorize("hasRole('ADMIN')")
	public com.packs.sharedlib.PageResponse<UserResponse> listAllUsers(
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int pageSize) {
		return userService.listAllUsers(page, pageSize);
	}

	@PostMapping("/admin/{userId}/role")
	@PreAuthorize("hasRole('ADMIN')")
	public UserResponse updateUserRole(@PathVariable UUID userId, @RequestBody Map<String, String> body) {
		String role = body.get("role");
		if (role == null || role.isBlank()) {
			throw new ApiException("ERR_VALIDATION", "A role value is required", HttpStatus.BAD_REQUEST);
		}
		return userService.updateUserRole(userId, role.toUpperCase());
	}

	private void requireOwnerOrAdmin(UUID targetUserId) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		boolean isAdmin = auth.getAuthorities().stream()
			.anyMatch(granted -> granted.getAuthority().equals("ROLE_ADMIN"));
		if (!isAdmin && !targetUserId.toString().equals(auth.getPrincipal())) {
			throw new ApiException("ERR_FORBIDDEN", "Access denied for this user", HttpStatus.FORBIDDEN);
		}
	}

	private UUID currentUserId() {
		return UUID.fromString((String) SecurityContextHolder.getContext().getAuthentication().getPrincipal());
	}
}