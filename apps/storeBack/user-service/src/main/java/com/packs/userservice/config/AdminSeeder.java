package com.packs.userservice.config;

import com.packs.userservice.entity.User;
import com.packs.userservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Bootstraps the very first ADMIN user. The role-management endpoint requires an
 * existing ADMIN, so without this a fresh deployment can never promote anyone.
 * Creates an admin only when no ADMIN account exists, and only if enabled.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final boolean enabled;
	private final String email;
	private final String password;
	private final String name;

	public AdminSeeder(
		UserRepository userRepository,
		PasswordEncoder passwordEncoder,
		@Value("${app.admin-seed.enabled:true}") boolean enabled,
		@Value("${app.admin-seed.email:admin@markit.com}") String email,
		@Value("${app.admin-seed.password:admin}") String password,
		@Value("${app.admin-seed.name:Markit Admin}") String name) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.enabled = enabled;
		this.email = email;
		this.password = password;
		this.name = name;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!enabled) {
			return;
		}
		boolean anyAdmin = userRepository.findAll().stream()
			.anyMatch(user -> "ADMIN".equalsIgnoreCase(user.getRole()));
		if (anyAdmin) {
			return;
		}
		if (userRepository.existsByEmail(email)) {
			log.warn("Default admin email {} already exists but has no ADMIN role; skipping auto-promote", email);
			return;
		}

		Instant now = Instant.now();
		User admin = new User();
		admin.setEmail(email);
		admin.setName(name);
		admin.setRole("ADMIN");
		admin.setPasswordHash(passwordEncoder.encode(password));
		admin.setCreatedAt(now);
		admin.setUpdatedAt(now);
		userRepository.save(admin);

		log.warn("Created bootstrap ADMIN user email={} (password from app.admin-seed.password). "
			+ "Disable app.admin-seed.enabled or change the password before going live.", email);
	}
}