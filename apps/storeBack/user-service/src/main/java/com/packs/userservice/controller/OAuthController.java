package com.packs.userservice.controller;

import com.packs.sharedlib.ApiException;
import com.packs.userservice.dto.AuthResponse;
import com.packs.userservice.service.GoogleOAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class OAuthController {

    private final GoogleOAuthService googleOAuthService;
    private final boolean googleOauthEnabled;

    public OAuthController(
        GoogleOAuthService googleOAuthService,
        @Value("${app.oauth.google.enabled:false}") boolean googleOauthEnabled) {
        this.googleOAuthService = googleOAuthService;
        this.googleOauthEnabled = googleOauthEnabled;
    }

    @GetMapping("/auth/oauth/google")
    public ResponseEntity<Void> initiateGoogleLogin() {
        if (!googleOauthEnabled) {
            throw new ApiException("ERR_OAUTH_DISABLED", "Google OAuth is not enabled", HttpStatus.NOT_IMPLEMENTED);
        }
        String authUrl = googleOAuthService.getGoogleAuthUrl();
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(authUrl)).build();
    }

    @GetMapping("/auth/oauth/google/callback")
    public ResponseEntity<AuthResponse> googleCallback(@RequestParam String code) {
        if (!googleOauthEnabled) {
            throw new ApiException("ERR_OAUTH_DISABLED", "Google OAuth is not enabled", HttpStatus.NOT_IMPLEMENTED);
        }
        return ResponseEntity.ok(googleOAuthService.handleCallback(code));
    }
}
