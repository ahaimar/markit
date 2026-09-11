package com.packs.userservice.controller;

import com.packs.sharedlib.ApiException;
import com.packs.userservice.dto.AuthResponse;
import com.packs.userservice.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class OAuthController {

	private final UserService userService;
	private final boolean googleOauthEnabled;
	private final String googleAuthUri;

	public OAuthController(
		UserService userService,
		@Value("${app.oauth.google.enabled:false}") boolean googleOauthEnabled,
		@Value("${app.oauth.google.auth-uri:https://accounts.google.com/o/oauth2/v2/auth}") String googleAuthUri) {
		this.userService = userService;
		this.googleOauthEnabled = googleOauthEnabled;
		this.googleAuthUri = googleAuthUri;
	}

	@GetMapping("/auth/oauth/google")
	public ResponseEntity<Void> initiateGoogleLogin() {
		if (!googleOauthEnabled) {
			throw new ApiException("ERR_OAUTH_DISABLED", "Google OAuth is not enabled", HttpStatus.NOT_IMPLEMENTED);
		}
		return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(googleAuthUri)).build();
	}

	@GetMapping("/auth/oauth/google/callback")
	public ResponseEntity<AuthResponse> googleCallback(@RequestParam String code) {
		if (!googleOauthEnabled) {
			throw new ApiException("ERR_OAUTH_DISABLED", "Google OAuth is not enabled", HttpStatus.NOT_IMPLEMENTED);
		}
		return ResponseEntity.ok(userService.googleSignInStub(code));
	}
}