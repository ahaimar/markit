package com.packs.sharedlib;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Component
public class JwtService {

	private final SecretKey key;
	private final long accessTokenTtlSeconds;
	private final long refreshTokenTtlSeconds;

	public JwtService(
		@Value("${app.jwt.secret}") String secret,
		@Value("${app.jwt.access-token-ttl-seconds:900}") long accessTokenTtlSeconds,
		@Value("${app.jwt.refresh-token-ttl-seconds:604800}") long refreshTokenTtlSeconds) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.accessTokenTtlSeconds = accessTokenTtlSeconds;
		this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
	}

	public String createToken(String userId, String email, List<String> roles) {
		return buildToken(userId, email, roles, accessTokenTtlSeconds);
	}

	public String createRefreshToken(String userId, String email, List<String> roles) {
		return buildToken(userId, email, roles, refreshTokenTtlSeconds);
	}

	public long getRefreshTokenTtlSeconds() {
		return refreshTokenTtlSeconds;
	}

	private String buildToken(String userId, String email, List<String> roles, long ttlSeconds) {
		Instant now = Instant.now();
		return Jwts.builder()
			.subject(userId)
			.claim("email", email)
			.claim("roles", roles)
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plusSeconds(ttlSeconds)))
			.signWith(key)
			.compact();
	}

	public Claims parseClaims(String token) {
		return Jwts.parser()
			.verifyWith(key)
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}

	public String parseUserId(String token) {
		return parseClaims(token).getSubject();
	}

	@SuppressWarnings("unchecked")
	public List<String> parseRoles(String token) {
		Object roles = parseClaims(token).get("roles");
		if (roles instanceof List<?> list) {
			return (List<String>) list;
		}
		return List.of();
	}

	public String parseEmail(String token) {
		return parseClaims(token).get("email", String.class);
	}

	public boolean isValid(String token) {
		try {
			parseClaims(token);
			return true;
		} catch (JwtException | IllegalArgumentException ex) {
			return false;
		}
	}
}