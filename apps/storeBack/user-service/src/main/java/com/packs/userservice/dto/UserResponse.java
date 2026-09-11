package com.packs.userservice.dto;

import com.packs.userservice.entity.User;

import java.time.Instant;

public record UserResponse(String id, String email, String name, String address, String role, Instant createdAt) {

	public static UserResponse from(User user) {
		return new UserResponse(
			user.getId().toString(),
			user.getEmail(),
			user.getName(),
			user.getAddress(),
			user.getRole(),
			user.getCreatedAt()
		);
	}

	public static UserResponse from(User user, java.util.UUID id) {
		return from(user);
	}
}