package com.packs.userservice.dto;

public record AuthResponse(String accessToken, String refreshToken, UserResponse user) {

	public static AuthResponse of(String accessToken, String refreshToken, UserResponse user) {
		return new AuthResponse(accessToken, refreshToken, user);
	}
}