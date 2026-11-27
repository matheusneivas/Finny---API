package com.finny.api.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {"app.rate-limit.enabled=true", "app.rate-limit.auth-per-minute=3",
    "app.rate-limit.avatar-per-hour=2"})
class RateLimitIntegrationTest extends IntegrationTestBase {

    /** Relógio fixo: o teste não cruza a virada da janela de 1 minuto. */
    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-26T12:00:10Z"), ZoneOffset.UTC);
        }
    }

    @Test
    void loginIsRateLimitedPerIp() throws Exception {
        String body = json(Map.of("email", "ninguem@example.com", "password", "Senha123"));
        // IP próprio: o cadastro feito em outros testes deste contexto não conta para este limite
        org.springframework.test.web.servlet.request.RequestPostProcessor fromIp = req -> {
            req.setRemoteAddr("10.9.8.7");
            return req;
        };
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body).with(fromIp))
                .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body).with(fromIp))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"));
    }

    @Test
    void avatarUploadIsRateLimitedPerHour() throws Exception {
        Session session = register();
        byte[] jpeg = AvatarIntegrationTest.image("jpg", 50, 50);
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(avatarPut(session, jpeg)).andExpect(status().isOk());
        }
        mockMvc.perform(avatarPut(session, jpeg))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"));
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder avatarPut(
            Session session, byte[] jpeg) {
        var builder = multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/users/me/avatar")
            .file(new org.springframework.mock.web.MockMultipartFile("file", "f.jpg", "image/jpeg", jpeg));
        builder.header("Authorization", bearer(session));
        return builder;
    }
}
