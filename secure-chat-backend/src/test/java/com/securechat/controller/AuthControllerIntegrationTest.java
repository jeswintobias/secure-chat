package com.securechat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securechat.dto.request.LoginRequest;
import com.securechat.dto.request.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link AuthController}.
 *
 * Uses a full Spring Boot context with H2 in-memory database (test profile).
 * Tests hit real REST endpoints and verify the full request → service → persistence pipeline.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private static final String REGISTER_URL = "/api/auth/register";
    private static final String LOGIN_URL = "/api/auth/login";

    // ======================== Registration Endpoints ========================

    @Test
    @Order(1)
    @DisplayName("POST /api/auth/register — valid request → 201 Created with JWT")
    void registerEndpoint_validRequest_returns201() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("integrationuser")
                .email("integration@test.com")
                .password("SecurePass1!")
                .confirmPassword("SecurePass1!")
                .build();

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value("integrationuser"))
                .andExpect(jsonPath("$.email").value("integration@test.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/auth/register — duplicate username → 409 Conflict")
    void registerEndpoint_duplicateUsername_returns409() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("integrationuser")
                .email("different@test.com")
                .password("SecurePass1!")
                .confirmPassword("SecurePass1!")
                .build();

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // ======================== Login Endpoints ========================

    @Test
    @Order(3)
    @DisplayName("POST /api/auth/login — valid credentials → 200 OK with JWT")
    void loginEndpoint_validCredentials_returns200() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("integrationuser")
                .password("SecurePass1!")
                .build();

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("integrationuser"));
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/auth/login — invalid password → 401 Unauthorized")
    void loginEndpoint_invalidCredentials_returns401() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("integrationuser")
                .password("WrongPassword1!")
                .build();

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
