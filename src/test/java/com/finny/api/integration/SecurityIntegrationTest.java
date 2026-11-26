package com.finny.api.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityIntegrationTest extends IntegrationTestBase {

    @Test
    void protectedRoutesRequireToken() throws Exception {
        mockMvc.perform(get("/api/v1/transactions"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Token inválido ou expirado"));
    }

    @Test
    void internalCallbackRequiresApiKey() throws Exception {
        String url = "/api/internal/callbacks/ocr/abc";
        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post(url).header("X-Internal-API-Key", "chave-errada-chave-errada-chave-errada")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post(url).header("X-Internal-API-Key", "test-internal-key-0123456789abcdef0123")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk());
    }

    @Test
    void userTokenCannotCallInternalRoutes() throws Exception {
        Session session = register();
        mockMvc.perform(post("/api/internal/callbacks/ocr/abc").header("Authorization", bearer(session))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyHealthActuatorIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownOriginIsNotAllowedByCors() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                .header("Origin", "https://site-malicioso.example")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void malformedJsonReturns400WithoutInternals() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{oops"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Corpo da requisição inválido ou mal formatado"));
    }
}
