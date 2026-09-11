package com.packs.userservice.dto;

import jakarta.validation.constraints.Email;

public record UpdateProfileRequest(
	@Email(message = "Email must be valid") String email,
	String name,
	String address
) {
}