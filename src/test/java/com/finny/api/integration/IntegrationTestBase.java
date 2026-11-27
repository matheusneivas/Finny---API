package com.finny.api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finny.api.domain.entity.Category;
import com.finny.api.domain.enums.TransactionType;
import com.finny.api.domain.repository.CategoryRepository;
import com.finny.api.infrastructure.ai.AiChatProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected CategoryRepository categoryRepository;

    @MockitoBean
    protected AiChatProvider aiChatProvider;

    protected record Session(String email, String accessToken, String refreshToken, UUID userId) {}

    protected Session register() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", "Senha123", "fullName", "Pessoa Teste",
                    "cpf", randomCpf(), "phone", "(11) 98765-4321"))))
            .andExpect(status().isCreated())
            .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Session(email, body.get("accessToken").asText(), body.get("refreshToken").asText(),
            UUID.fromString(body.path("user").path("id").asText()));
    }

    protected Category defaultCategory(String name, TransactionType type) {
        return categoryRepository.findAllDefault().stream()
            .filter(c -> c.getName().equals(name))
            .findFirst()
            .orElseGet(() -> {
                Category c = new Category();
                c.setName(name);
                c.setIsDefault(true);
                c.setTransactionType(type);
                return categoryRepository.save(c);
            });
    }

    /** CPF aleatório válido (dígitos verificadores corretos). */
    protected static String randomCpf() {
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        int[] d = new int[11];
        for (int i = 0; i < 9; i++) d[i] = r.nextInt(10);
        for (int pos = 9; pos < 11; pos++) {
            int sum = 0;
            for (int i = 0; i < pos; i++) sum += d[i] * (pos + 1 - i);
            int v = 11 - (sum % 11);
            d[pos] = v >= 10 ? 0 : v;
        }
        StringBuilder sb = new StringBuilder();
        for (int x : d) sb.append(x);
        return sb.toString();
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected static String bearer(Session session) {
        return "Bearer " + session.accessToken();
    }
}
