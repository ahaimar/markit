package com.packs.userservice.service;

import com.packs.sharedlib.ApiException;
import com.packs.sharedlib.JwtService;
import com.packs.sharedlib.UserRegisteredEvent;
import com.packs.userservice.dto.AuthResponse;
import com.packs.userservice.dto.UserResponse;
import com.packs.userservice.entity.RefreshToken;
import com.packs.userservice.entity.User;
import com.packs.userservice.entity.UserOutboxEvent;
import com.packs.userservice.repository.RefreshTokenRepository;
import com.packs.userservice.repository.UserOutboxEventRepository;
import com.packs.userservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GoogleOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserOutboxEventRepository userOutboxEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final RestTemplate restTemplate;
    private final String googleRedirectUri;

    public GoogleOAuthService(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        UserOutboxEventRepository userOutboxEventRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        ObjectMapper objectMapper,
        ClientRegistrationRepository clientRegistrationRepository,
        @Value("${app.oauth.google.redirect-uri:http://localhost:8080/auth/oauth/google/callback}") String googleRedirectUri) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userOutboxEventRepository = userOutboxEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.restTemplate = new RestTemplate();
        this.googleRedirectUri = googleRedirectUri;
    }

    public String getGoogleAuthUrl() {
        ClientRegistration registration = clientRegistrationRepository.findByRegistrationId("google");
        if (registration == null) {
            throw new ApiException("ERR_OAUTH_NOT_CONFIGURED", "Google OAuth is not configured", HttpStatus.NOT_IMPLEMENTED);
        }

        String authUri = registration.getProviderDetails().getAuthorizationUri();
        String clientId = registration.getClientId();
        String scope = String.join(" ", registration.getScopes());

        return authUri + "?client_id=" + clientId
            + "&redirect_uri=" + googleRedirectUri
            + "&response_type=code"
            + "&scope=" + scope
            + "&access_type=online"
            + "&prompt=select_account";
    }

    @Transactional
    public AuthResponse handleCallback(String code) {
        if (code == null || code.isBlank()) {
            throw new ApiException("ERR_INVALID_OAUTH_CODE", "OAuth code is required", HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> tokenResponse = exchangeCodeForTokens(code);
        String accessToken = (String) tokenResponse.get("access_token");
        if (accessToken == null) {
            throw new ApiException("ERR_OAUTH_TOKEN_EXCHANGE", "Failed to exchange code for access token", HttpStatus.BAD_GATEWAY);
        }

        Map<String, Object> userInfo = fetchUserInfo(accessToken);
        String email = (String) userInfo.get("email");
        String name = (String) userInfo.get("name");

        if (email == null) {
            throw new ApiException("ERR_OAUTH_NO_EMAIL", "Google account does not have an email", HttpStatus.BAD_GATEWAY);
        }

        String oauthEmail = email.toLowerCase();
        User user = userRepository.findByEmail(oauthEmail).orElseGet(() -> createGoogleUser(oauthEmail, name));

        return issueTokens(user);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> exchangeCodeForTokens(String code) {
        ClientRegistration registration = clientRegistrationRepository.findByRegistrationId("google");
        if (registration == null) {
            throw new ApiException("ERR_OAUTH_NOT_CONFIGURED", "Google OAuth is not configured", HttpStatus.NOT_IMPLEMENTED);
        }

        String tokenUri = registration.getProviderDetails().getTokenUri();

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(OAuth2ParameterNames.CODE, code);
        params.add(OAuth2ParameterNames.CLIENT_ID, registration.getClientId());
        params.add(OAuth2ParameterNames.CLIENT_SECRET, registration.getClientSecret());
        params.add(OAuth2ParameterNames.REDIRECT_URI, googleRedirectUri);
        params.add(OAuth2ParameterNames.GRANT_TYPE, "authorization_code");

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUri, request, Map.class);
            Map<String, Object> body = response.getBody();

            if (body == null) {
                throw new ApiException("ERR_OAUTH_TOKEN_EXCHANGE", "Empty response from Google token endpoint", HttpStatus.BAD_GATEWAY);
            }
            return body;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to exchange OAuth code for tokens: {}", ex.getMessage());
            throw new ApiException("ERR_OAUTH_TOKEN_EXCHANGE", "Failed to exchange code for tokens: " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchUserInfo(String accessToken) {
        String userInfoUri = "https://www.googleapis.com/oauth2/v3/userinfo";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(userInfoUri, HttpMethod.GET, request, Map.class);
            Map<String, Object> body = response.getBody();

            if (body == null) {
                throw new ApiException("ERR_OAUTH_USERINFO", "Empty response from Google userinfo endpoint", HttpStatus.BAD_GATEWAY);
            }
            return body;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to fetch Google user info: {}", ex.getMessage());
            throw new ApiException("ERR_OAUTH_USERINFO", "Failed to fetch user info: " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    private User createGoogleUser(String email, String name) {
        Instant now = Instant.now();
        User user = new User();
        user.setEmail(email);
        user.setName(name != null ? name : email.split("@")[0]);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setRole("CUSTOMER");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        User saved = userRepository.save(user);
        enqueueUserRegistered(saved.getId(), saved.getEmail(), saved.getName());
        log.info("Created new user from Google OAuth: {}", email);
        return saved;
    }

    private void enqueueUserRegistered(UUID userId, String email, String name) {
        UUID eventId = UUID.randomUUID();
        UserRegisteredEvent event = new UserRegisteredEvent(eventId.toString(), userId.toString(), email, name, Instant.now());
        try {
            UserOutboxEvent outbox = new UserOutboxEvent();
            outbox.setEventType("UserRegistered");
            outbox.setPayload(objectMapper.writeValueAsString(event));
            outbox.setPublished(false);
            outbox.setCreatedAt(Instant.now());
            userOutboxEventRepository.save(outbox);
        } catch (JacksonException ex) {
            log.error("Failed to serialize UserRegistered event for user {}", userId, ex);
        }
    }

    private AuthResponse issueTokens(User user) {
        List<String> roles = List.of(user.getRole());
        String accessToken = jwtService.createToken(user.getId().toString(), user.getEmail(), roles);
        String refreshToken = jwtService.createRefreshToken(user.getId().toString(), user.getEmail(), roles);
        persistRefreshToken(user.getId(), refreshToken);
        return AuthResponse.of(accessToken, refreshToken, UserResponse.from(user));
    }

    private void persistRefreshToken(UUID userId, String rawRefreshToken) {
        refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });

        Instant now = Instant.now();
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(hash(rawRefreshToken));
        token.setIssuedAt(now);
        token.setExpiresAt(now.plusSeconds(jwtService.getRefreshTokenTtlSeconds()));
        refreshTokenRepository.save(token);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
