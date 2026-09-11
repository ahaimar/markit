package com.packs.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
	@NotBlank(message = "Current password is required")
	String currentPassword,

	@NotBlank(message = "New password is required")
	@Size(min = 8, message = "Password must be at least 8 characters")
	@Pattern(regexp = ".*[A-Z].*", message = "Password must contain an uppercase letter")
	@Pattern(regexp = ".*\\d.*", message = "Password must contain a digit")
	String newPassword
) {
}