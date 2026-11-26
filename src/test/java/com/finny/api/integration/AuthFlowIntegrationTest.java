package com.finny.api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIntegrationTest extends IntegrationTestBase {

    @Test
    void loginIsCaseInsensitiveAndReturnsExpiresIn() throws Exception {
        Session session = register();

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", session.email().toUpperCase(), "password", "Senha123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expiresIn").value(1800))
            .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void wrongPasswordReturnsGenericError() throws Exception {
        Session session = register();
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", session.email(), "password", "errada123"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("E-mail ou senha incorretos"));
    }

    @Test
    void refreshTokenRotatesAndReuseRevokesTheSession() throws Exception {
        Session session = register();

        String rotated = refresh(session.refreshToken(), 200);
        assertThat(rotated).isNotEqualTo(session.refreshToken());

        // Reusar o refresh token antigo derruba a sessão inteira...
        refresh(session.refreshToken(), 401);
        // ...inclusive o token novo
        refresh(rotated, 401);
    }

    @Test
    void refreshTokenIsNotAcceptedAsAccessToken() throws Exception {
        Session session = register();
        mockMvc.perform(get("/api/v1/chat/stats").header("Authorization", "Bearer " + session.refreshToken()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        Session session = register();
        mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", session.refreshToken()))))
            .andExpect(status().isOk());
        refresh(session.refreshToken(), 401);
    }

    @Test
    void weakPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "fraca@example.com", "password", "123", "fullName", "X"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.password").exists());
    }

    private String refresh(String refreshToken, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", refreshToken))))
            .andExpect(status().is(expectedStatus))
            .andReturn();
        if (expectedStatus != 200) return null;
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("refreshToken").asText();
    }
}
