package com.finny.api.integration;

import com.finny.api.domain.entity.Category;
import com.finny.api.domain.enums.TransactionType;
import com.finny.api.domain.repository.TransactionRepository;
import com.finny.api.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountLgpdIntegrationTest extends IntegrationTestBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void exportReturnsOwnDataOnly() throws Exception {
        Session a = register();
        Session b = register();
        addData(a);

        mockMvc.perform(get("/api/v1/users/me/export").header("Authorization", bearer(a)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"meus-dados-finny.json\""))
            .andExpect(jsonPath("$.conta.email").value(a.email()))
            .andExpect(jsonPath("$.conta.celular").value("11987654321"))
            .andExpect(jsonPath("$.lancamentos.length()").value(1))
            .andExpect(jsonPath("$.lancamentos[0].descricao").value("Mercado"))
            .andExpect(jsonPath("$.metas[0].nome").value("Viagem"));

        mockMvc.perform(get("/api/v1/users/me/export").header("Authorization", bearer(b)))
            .andExpect(jsonPath("$.lancamentos.length()").value(0))
            .andExpect(jsonPath("$.metas.length()").value(0));

        mockMvc.perform(get("/api/v1/users/me/export")).andExpect(status().isUnauthorized());
    }

    @Test
    void deleteAccountNeedsPasswordAndRemovesEverything() throws Exception {
        Session session = register();
        addData(session);

        mockMvc.perform(post("/api/v1/users/me/delete").header("Authorization", bearer(session))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("password", "SenhaErrada1"))))
            .andExpect(status().isUnprocessableEntity());
        assertThat(userRepository.findById(session.userId())).isPresent();

        mockMvc.perform(post("/api/v1/users/me/delete").header("Authorization", bearer(session))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("password", "Senha123"))))
            .andExpect(status().isNoContent());

        assertThat(userRepository.findById(session.userId())).isEmpty();
        assertThat(transactionRepository.countByUserIdAndDeletedFalse(session.userId())).isZero();

        // O token antigo e o refresh token não servem mais
        mockMvc.perform(get("/api/v1/transactions").header("Authorization", bearer(session)))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", session.refreshToken()))))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", session.email(), "password", "Senha123"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void registerRecordsTermsAcceptance() throws Exception {
        String email = "termos-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", "Senha123", "fullName", "Pessoa Teste",
                    "cpf", randomCpf(), "phone", "(11) 98765-4321", "acceptedTerms", true))))
            .andExpect(status().isCreated());
        var user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getTermsAcceptedAt()).isNotNull();
        assertThat(user.getTermsVersion()).isNotBlank();
    }

    @Test
    void loginLocksAccountAfterManyWrongPasswords() throws Exception {
        Session session = register();
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("email", session.email(), "password", "Errada" + i + "Aa"))))
                .andExpect(status().isUnauthorized());
        }
        // Mesmo com a senha certa, espera a trava acabar
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", session.email(), "password", "Senha123"))))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"));
    }

    @Test
    void apiResponsesCarrySecurityHeaders() throws Exception {
        Session session = register();
        mockMvc.perform(get("/api/v1/transactions").header("Authorization", bearer(session)))
            .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
            .andExpect(header().exists("Permissions-Policy"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    private void addData(Session session) throws Exception {
        Category food = defaultCategory("Alimentação", TransactionType.EXPENSE);
        mockMvc.perform(post("/api/v1/transactions").header("Authorization", bearer(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("amount", 50, "type", "EXPENSE", "categoryId", food.getId().toString(),
                    "description", "Mercado", "transactionDate", LocalDate.now().toString(),
                    "frequency", "ONCE", "repeatCount", 1))))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/goals").header("Authorization", bearer(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Viagem", "targetAmount", 3000,
                    "deadline", LocalDate.now().plusYears(1).toString()))))
            .andExpect(status().isCreated());
    }
}
