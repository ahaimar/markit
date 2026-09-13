package com.packs.userservice.service;

import com.packs.sharedlib.ApiException;
import com.packs.sharedlib.JwtService;
import com.packs.sharedlib.UserRegisteredEvent;
import com.packs.userservice.dto.AuthResponse;
import com.packs.userservice.dto.ChangePasswordRequest;
import com.packs.userservice.dto.LoginRequest;
import com.packs.userservice.dto.RefreshTokenRequest;
import com.packs.userservice.dto.RegisterRequest;
import com.packs.userservice.dto.UpdateProfileRequest;
import com.packs.userservice.dto.UserResponse;
import com.packs.userservice.entity.RefreshToken;
import com.packs.userservice.entity.User;
import com.packs.userservice.entity.UserOutboxEvent;
import com.packs.userservice.repository.RefreshTokenRepository;
import com.packs.userservice.repository.UserOutboxEventRepository;
import com.packs.userservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {

	private static final Logger log = LoggerFactory.getLogger(UserService.class);

	private static final String EMAIL_TAKEN = "ERR_EMAIL_TAKEN";
	private static final String INVALID_CREDENTIALS = "ERR_INVALID_CREDENTIALS";
	private static final String USER_NOT_FOUND = "ERR_USER_NOT_FOUND";
	private static final String INVALID_REFRESH_TOKEN = "ERR_INVALID_REFRESH_TOKEN";

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final UserOutboxEventRepository userOutboxEventRepository;
	private final ObjectMapper objectMapper;

	public UserService(
		UserRepository userRepository,
		RefreshTokenRepository refreshTokenRepository,
		PasswordEncoder passwordEncoder,
		JwtService jwtService,
		UserOutboxEventRepository userOutboxEventRepository,
		ObjectMapper objectMapper) {
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.userOutboxEventRepository = userOutboxEventRepository;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		if (userRepository.existsByEmail(request.email().toLowerCase())) {
			throw new ApiException(EMAIL_TAKEN, "A user with this email already exists", HttpStatus.CONFLICT);
		}

		Instant now = Instant.now();
		User user = new User();
		user.setEmail(request.email().toLowerCase());
		user.setName(request.name());
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		user.setRole("CUSTOMER");
		user.setCreatedAt(now);
		user.setUpdatedAt(now);

		User saved = userRepository.save(user);
		enqueueUserRegistered(saved.getId(), saved.getEmail(), saved.getName());
		return issueTokens(saved);
	}

	@Transactional
	public AuthResponse login(LoginRequest request) {
		User user = userRepository.findByEmailAndDeletedAtIsNull(request.email().toLowerCase())
			.orElseThrow(() -> new ApiException(INVALID_CREDENTIALS, "Invalid email or password", HttpStatus.UNAUTHORIZED));

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new ApiException(INVALID_CREDENTIALS, "Invalid email or password", HttpStatus.UNAUTHORIZED);
		}

		return issueTokens(user);
	}

	@Transactional
	public AuthResponse refresh(RefreshTokenRequest request) {
		String hash = hash(request.refreshToken());
		RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
			.orElseThrow(() -> new ApiException(INVALID_REFRESH_TOKEN, "Invalid refresh token", HttpStatus.UNAUTHORIZED));

		if (!stored.isActive()) {
		 throw new ApiException(INVALID_REFRESH_TOKEN, "Refresh token expired or revoked", HttpStatus.UNAUTHORIZED);
		}

		User user = userRepository.findByIdAndDeletedAtIsNull(stored.getUserId())
			.orElseThrow(() -> new ApiException(USER_NOT_FOUND, "User not found", HttpStatus.NOT_FOUND));

		stored.setRevokedAt(Instant.now());
		refreshTokenRepository.save(stored);

		return issueTokens(user);
	}

	@Transactional
	public void logout(RefreshTokenRequest request) {
		String hash = hash(request.refreshToken());
		refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
			token.setRevokedAt(Instant.now());
			refreshTokenRepository.save(token);
		});
	}

	@Transactional(readOnly = true)
	public UserResponse getById(UUID userId) {
		User user = findActiveUser(userId);
		return UserResponse.from(user);
	}

	@Transactional
	public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
		User user = findActiveUser(userId);

		if (request.email() != null && !request.email().isBlank() && !request.email().equalsIgnoreCase(user.getEmail())) {
			String email = request.email().toLowerCase();
			if (userRepository.existsByEmail(email)) {
				throw new ApiException(EMAIL_TAKEN, "A user with this email already exists", HttpStatus.CONFLICT);
			}
			user.setEmail(email);
		}
		if (request.name() != null && !request.name().isBlank()) {
			user.setName(request.name());
		}
		if (request.address() != null) {
			user.setAddress(request.address());
		}
		user.setUpdatedAt(Instant.now());

		return UserResponse.from(userRepository.save(user));
	}

	@Transactional
	public void changePassword(UUID userId, ChangePasswordRequest request) {
		User user = findActiveUser(userId);

		if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw new ApiException("ERR_INVALID_PASSWORD", "Current password is incorrect", HttpStatus.BAD_REQUEST);
		}

		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		user.setUpdatedAt(Instant.now());
		userRepository.save(user);

		refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId)
			.ifPresent(token -> {
				token.setRevokedAt(Instant.now());
				refreshTokenRepository.save(token);
			});
	}

	@Transactional
	public void softDelete(UUID userId) {
		User user = findActiveUser(userId);
		user.setDeletedAt(Instant.now());
		user.setUpdatedAt(Instant.now());
		userRepository.save(user);
	}

	@Transactional(readOnly = true)
	public com.packs.sharedlib.PageResponse<UserResponse> listAllUsers(int page, int pageSize) {
		org.springframework.data.domain.Page<User> users = userRepository.findByDeletedAtIsNullOrderByCreatedAtAsc(
			org.springframework.data.domain.PageRequest.of(
				Math.max(page, 0),
				Math.min(Math.max(pageSize, 1), 100)));
		List<UserResponse> dtos = users.getContent().stream().map(UserResponse::from).toList();
		return com.packs.sharedlib.PageResponse.of(dtos, users.getNumber(), users.getSize(), users.getTotalElements());
	}

	@Transactional
	public UserResponse updateUserRole(UUID userId, String role) {
		User user = findActiveUser(userId);
		user.setRole(role);
		user.setUpdatedAt(Instant.now());
		return UserResponse.from(userRepository.save(user));
	}

	@Transactional
	public AuthResponse googleSignInStub(String code) {
		if (code == null || code.isBlank()) {
			throw new ApiException("ERR_INVALID_OAUTH_CODE", "OAuth code is required", HttpStatus.BAD_REQUEST);
		}
		String stubEmail = "google." + code.hashCode() + "@stub.markit.local";
		java.util.concurrent.atomic.AtomicBoolean created = new java.util.concurrent.atomic.AtomicBoolean(false);
		User user = userRepository.findByEmail(stubEmail).orElseGet(() -> {
			created.set(true);
			Instant now = Instant.now();
			User newUser = new User();
			newUser.setEmail(stubEmail);
			newUser.setName("Google User");
			newUser.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
			newUser.setRole("CUSTOMER");
			newUser.setCreatedAt(now);
			newUser.setUpdatedAt(now);
			return userRepository.save(newUser);
		});
		if (created.get()) {
			enqueueUserRegistered(user.getId(), user.getEmail(), user.getName());
		}
		return issueTokens(user);
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
			log.info("Enqueued UserRegistered outbox event for user {}", userId);
		} catch (JacksonException ex) {
			log.error("Failed to serialize UserRegistered event for user {}", userId, ex);
		}
	}

	private User findActiveUser(UUID userId) {
		return userRepository.findByIdAndDeletedAtIsNull(userId)
			.orElseThrow(() -> new ApiException(USER_NOT_FOUND, "User not found", HttpStatus.NOT_FOUND));
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