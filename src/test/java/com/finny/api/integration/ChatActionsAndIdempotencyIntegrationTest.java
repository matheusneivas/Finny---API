package com.finny.api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.finny.api.domain.entity.Category;
import com.finny.api.domain.enums.TransactionType;
import com.finny.api.domain.repository.TransactionRepository;
import com.finny.api.infrastructure.ai.AiChatResult;
import com.finny.api.infrastructure.ai.AiToolHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChatActionsAndIdempotencyIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TransactionRepository transactionRepository;

    private Category food;

    @BeforeEach
    void setUp() {
        food = defaultCategory("Alimentação", TransactionType.EXPENSE);
    }

    // ── Estado dos cartões ─────────────────────────────────────────────────

    @Test
    void cardStatusIsStoredAndReturnedInHistory() throws Exception {
        Session session = register();
        JsonNode action = proposeTransaction(session);
        String actionId = action.get("id").asText();
        assertThat(action.get("status").asText()).isEqualTo("PENDING");

        historyFirstAction(session).andExpect(jsonPath("$.content[0].proposedActions[0].status").value("PENDING"));

        putStatus(session, actionId, "DISCARDED").andExpect(status().isNoContent());
        historyFirstAction(session).andExpect(jsonPath("$.content[0].proposedActions[0].status").value("DISCARDED"));

        // Pode mudar de ideia depois de descartar
        putStatus(session, actionId, "CONFIRMED").andExpect(status().isNoContent());
        putStatus(session, actionId, "CONFIRMED").andExpect(status().isNoContent()); // idempotente
        historyFirstAction(session).andExpect(jsonPath("$.content[0].proposedActions[0].status").value("CONFIRMED"));

        // Confirmado é definitivo
        putStatus(session, actionId, "DISCARDED").andExpect(status().isConflict());
        putStatus(session, actionId, "PENDING").andExpect(status().isBadRequest());
    }

    @Test
    void cannotChangeAnotherUsersCard() throws Exception {
        Session owner = register();
        Session other = register();
        String actionId = proposeTransaction(owner).get("id").asText();

        putStatus(other, actionId, "CONFIRMED").andExpect(status().isNotFound());
        putStatus(owner, UUID.randomUUID().toString(), "CONFIRMED").andExpect(status().isNotFound());
        putStatus(owner, "x".repeat(200), "CONFIRMED").andExpect(status().isNotFound());
    }

    @Test
    void invalidStatusIsRejected() throws Exception {
        Session session = register();
        String actionId = proposeTransaction(session).get("id").asText();
        mockMvc.perform(put("/api/v1/chat/actions/" + actionId).header("Authorization", bearer(session))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"TALVEZ\"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/chat/actions/" + actionId).header("Authorization", bearer(session))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }

    // ── Idempotency-Key ────────────────────────────────────────────────────

    @Test
    void confirmingTheSameCardTwiceCreatesOneTransactionAndMarksTheCard() throws Exception {
        Session session = register();
        JsonNode action = proposeTransaction(session);
        String actionId = action.get("id").asText();
        String payload = action.get("payload").toString();

        MvcResult first = createTransaction(session, actionId, payload)
            .andExpect(status().isCreated()).andReturn();
        MvcResult second = createTransaction(session, actionId, payload)
            .andExpect(status().isCreated())
            .andExpect(header().string("Idempotent-Replayed", "true"))
            .andReturn();

        String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();
        String secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();
        assertThat(secondId).isEqualTo(firstId);
        assertThat(transactionRepository.countByUserIdAndDeletedFalse(session.userId())).isEqualTo(1);

        // O cartão vira CONFIRMED sozinho, mesmo se o app não avisar
        historyFirstAction(session).andExpect(jsonPath("$.content[0].proposedActions[0].status").value("CONFIRMED"));
    }

    @Test
    void keysAreScopedPerUserAndPerOperation() throws Exception {
        Session a = register();
        Session b = register();
        String payload = json(Map.of("amount", 10, "type", "EXPENSE", "categoryId", food.getId().toString(),
            "description", "Café", "transactionDate", LocalDate.now().toString(), "frequency", "ONCE", "repeatCount", 1));

        createTransaction(a, "chave-1", payload).andExpect(status().isCreated());
        createTransaction(b, "chave-1", payload).andExpect(status().isCreated()).andExpect(header().doesNotExist("Idempotent-Replayed"));
        assertThat(transactionRepository.countByUserIdAndDeletedFalse(b.userId())).isEqualTo(1);

        // Mesma chave em outra operação: recusada
        mockMvc.perform(post("/api/v1/goals").header("Authorization", bearer(a)).header("Idempotency-Key", "chave-1")
                .contentType(MediaType.APPLICATION_JSON).content(goalJson()))
            .andExpect(status().isUnprocessableEntity());

        // Sem cabeçalho, nada muda
        createTransaction(a, null, payload).andExpect(status().isCreated());
        createTransaction(a, null, payload).andExpect(status().isCreated());
        assertThat(transactionRepository.countByUserIdAndDeletedFalse(a.userId())).isEqualTo(3);

        // Chave inválida
        createTransaction(a, "x".repeat(65), payload).andExpect(status().isBadRequest());
        createTransaction(a, "tem espaço", payload).andExpect(status().isBadRequest());
    }

    @Test
    void goalCreationAndContributionAreIdempotent() throws Exception {
        Session session = register();
        MvcResult g1 = mockMvc.perform(post("/api/v1/goals").header("Authorization", bearer(session))
                .header("Idempotency-Key", "meta-1").contentType(MediaType.APPLICATION_JSON).content(goalJson()))
            .andExpect(status().isCreated()).andReturn();
        MvcResult g2 = mockMvc.perform(post("/api/v1/goals").header("Authorization", bearer(session))
                .header("Idempotency-Key", "meta-1").contentType(MediaType.APPLICATION_JSON).content(goalJson()))
            .andExpect(status().isCreated()).andReturn();
        String goalId = objectMapper.readTree(g1.getResponse().getContentAsString()).get("id").asText();
        assertThat(objectMapper.readTree(g2.getResponse().getContentAsString()).get("id").asText()).isEqualTo(goalId);

        mockMvc.perform(get("/api/v1/goals").header("Authorization", bearer(session)))
            .andExpect(jsonPath("$.length()").value(1));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/goals/" + goalId + "/contribute").header("Authorization", bearer(session))
                    .header("Idempotency-Key", "aporte-1").contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("amount", 100))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedAmount").value(100));
        }
        mockMvc.perform(get("/api/v1/goals/" + goalId).header("Authorization", bearer(session)))
            .andExpect(jsonPath("$.savedAmount").value(100));
    }

    @Test
    void failedCreationDoesNotBurnTheKey() throws Exception {
        Session session = register();
        String invalid = json(Map.of("amount", -5, "type", "EXPENSE"));
        createTransaction(session, "k-erro", invalid).andExpect(status().isBadRequest());

        String valid = json(Map.of("amount", 10, "type", "EXPENSE", "categoryId", food.getId().toString(),
            "description", "Café", "transactionDate", LocalDate.now().toString(), "frequency", "ONCE", "repeatCount", 1));
        createTransaction(session, "k-erro", valid).andExpect(status().isCreated())
            .andExpect(header().doesNotExist("Idempotent-Replayed"));
    }

    // ── Auxiliares ─────────────────────────────────────────────────────────

    private JsonNode proposeTransaction(Session session) throws Exception {
        when(aiChatProvider.chat(any(), any())).thenAnswer(inv -> {
            AiToolHandler tools = inv.getArgument(1);
            tools.handle("propose_create_transaction", Map.of(
                "description", "Mercado", "amount", 52.5, "type", "EXPENSE",
                "category_name", "alimentacao", "transaction_date", LocalDate.now().toString()));
            return new AiChatResult("Preparei o lançamento.", 10, 10, false);
        });
        MvcResult result = mockMvc.perform(post("/api/v1/chat/messages").header("Authorization", bearer(session))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("message", "gastei 52,50 no mercado"))))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("proposedActions").path(0);
    }

    private org.springframework.test.web.servlet.ResultActions historyFirstAction(Session session) throws Exception {
        return mockMvc.perform(get("/api/v1/chat/history").header("Authorization", bearer(session)))
            .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions putStatus(Session session, String actionId, String status)
            throws Exception {
        return mockMvc.perform(put("/api/v1/chat/actions/" + actionId).header("Authorization", bearer(session))
            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", status))));
    }

    private org.springframework.test.web.servlet.ResultActions createTransaction(Session session, String key, String body)
            throws Exception {
        var builder = post("/api/v1/transactions").header("Authorization", bearer(session))
            .contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) builder.header("Idempotency-Key", key);
        return mockMvc.perform(builder);
    }

    private String goalJson() throws Exception {
        return json(Map.of("name", "Carro", "targetAmount", 30000, "deadline", LocalDate.now().plusYears(1).toString()));
    }
}
