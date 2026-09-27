package com.packs.userservice.controller;

import com.packs.sharedlib.JwtService;
import com.packs.userservice.dto.LoginRequest;
import com.packs.userservice.dto.RegisterRequest;
import com.packs.userservice.entity.User;
import com.packs.userservice.repository.RefreshTokenRepository;
import com.packs.userservice.repository.UserRepository;
import com.packs.userservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void register_validRequest_createsUserAndReturnsTokens() throws Exception {
        RegisterRequest request = new RegisterRequest("test@example.com", "Password1", "Test User", null);

        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())
            .andExpect(jsonPath("$.user.email").value("test@example.com"))
            .andExpect(jsonPath("$.user.name").value("Test User"))
            .andExpect(jsonPath("$.user.role").value("CUSTOMER"));
    }

    @Test
    void register_duplicateEmail_returnsConflict() throws Exception {
        createUser("dup@example.com", "Password1", "Existing User");

        RegisterRequest request = new RegisterRequest("dup@example.com", "Password1", "New User", null);

        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ERR_EMAIL_TAKEN"));
    }

    @Test
    void register_invalidEmail_returnsBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest("not-an-email", "Password1", "Test User", null);

        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void register_weakPassword_returnsBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest("test@example.com", "weak", "Test User", null);

        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void login_validCredentials_returnsTokens() throws Exception {
        createUser("login@example.com", "Password1", "Login User");

        LoginRequest request = new LoginRequest("login@example.com", "Password1");

        mockMvc.perform(post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())
            .andExpect(jsonPath("$.user.email").value("login@example.com"));
    }

    @Test
    void login_invalidCredentials_returnsUnauthorized() throws Exception {
        createUser("login2@example.com", "Password1", "Login User");

        LoginRequest request = new LoginRequest("login2@example.com", "WrongPassword");

        mockMvc.perform(post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("ERR_INVALID_CREDENTIALS"));
    }

    @Test
    void login_nonExistentUser_returnsUnauthorized() throws Exception {
        LoginRequest request = new LoginRequest("nonexistent@example.com", "Password1");

        mockMvc.perform(post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("ERR_INVALID_CREDENTIALS"));
    }

    @Test
    void refresh_validToken_returnsNewTokens() throws Exception {
        User user = createUser("refresh@example.com", "Password1", "Refresh User");
        String refreshToken = jwtService.createRefreshToken(user.getId().toString(), user.getEmail(), List.of("CUSTOMER"));

        mockMvc.perform(post("/api/users/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    void refresh_invalidToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/users/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"invalid-token\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("ERR_INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logout_validToken_revokesRefreshToken() throws Exception {
        User user = createUser("logout@example.com", "Password1", "Logout User");
        String refreshToken = jwtService.createRefreshToken(user.getId().toString(), user.getEmail(), List.of("CUSTOMER"));

        mockMvc.perform(post("/api/users/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .andExpect(status().isNoContent());
    }

    private User createUser(String email, String password, String name) {
        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole("CUSTOMER");
        user.setCreatedAt(java.time.Instant.now());
        user.setUpdatedAt(java.time.Instant.now());
        return userRepository.save(user);
    }
}
