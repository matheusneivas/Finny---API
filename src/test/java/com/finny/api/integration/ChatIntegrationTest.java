package com.finny.api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.finny.api.application.service.assistant.AssistantPromptBuilder;
import com.finny.api.domain.entity.Category;
import com.finny.api.domain.enums.TransactionType;
import com.finny.api.domain.repository.TransactionRepository;
import com.finny.api.infrastructure.ai.AiChatRequest;
import com.finny.api.infrastructure.ai.AiChatResult;
import com.finny.api.infrastructure.ai.AiProviderException;
import com.finny.api.infrastructure.ai.AiToolHandler;
import com.finny.api.infrastructure.ai.AiToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "app.chat.free-monthly-limit=3")
class ChatIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TransactionRepository transactionRepository;

    private Category food;

    @BeforeEach
    void setUp() {
        food = defaultCategory("Alimentação", TransactionType.EXPENSE);
    }

    @Test
    void proposalIsReturnedButNothingIsSaved() throws Exception {
        Session session = register();
        AtomicReference<AiToolResult> toolResult = new AtomicReference<>();
        when(aiChatProvider.chat(any(), any())).thenAnswer(inv -> {
            AiToolHandler tools = inv.getArgument(1);
            toolResult.set(tools.handle("propose_create_transaction", Map.of(
                "description", "Mercado", "amount", 52.5, "type", "EXPENSE",
                "category_name", "alimentacao", "transaction_date", "2026-11-20")));
            return new AiChatResult("Preparei o lançamento, é só confirmar.", 100, 20, false);
        });

        MvcResult result = sendChat(session, "gastei 52,50 no mercado dia 20")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.proposedActions.length()").value(1))
            .andExpect(jsonPath("$.proposedActions[0].type").value("CREATE_TRANSACTION"))
            .andExpect(jsonPath("$.proposedActions[0].payload.amount").value(52.5))
            .andExpect(jsonPath("$.proposedActions[0].payload.categoryId").value(food.getId().toString()))
            .andExpect(jsonPath("$.proposedActions[0].payload.transactionDate").value("2026-11-20"))
            .andExpect(jsonPath("$.messagesUsedThisMonth").value(1))
            .andReturn();

        assertThat(toolResult.get().error()).isFalse();
        assertThat(transactionRepository.countByUserIdAndDeletedFalse(session.userId())).isZero();

        // O payload serve direto no endpoint REST de criação
        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString())
            .path("proposedActions").path(0).path("payload");
        mockMvc.perform(post("/api/v1/transactions").header("Authorization", bearer(session))
                .contentType(MediaType.APPLICATION_JSON).content(payload.toString()))
            .andExpect(status().isCreated());

        // Histórico guarda o cartão
        mockMvc.perform(get("/api/v1/chat/history").header("Authorization", bearer(session)))
            .andExpect(jsonPath("$.content[0].role").value("ASSISTANT"))
            .andExpect(jsonPath("$.content[0].proposedActions[0].type").value("CREATE_TRANSACTION"));
    }

    @Test
    void promptCarriesStaticInstructionsAndTools() throws Exception {
        Session session = register();
        when(aiChatProvider.chat(any(), any())).thenReturn(new AiChatResult("Oi!", 1, 1, false));

        sendChat(session, "oi").andExpect(status().isOk());

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiChatProvider).chat(captor.capture(), any());
        AiChatRequest request = captor.getValue();
        assertThat(request.staticInstructions()).isEqualTo(AssistantPromptBuilder.STATIC_INSTRUCTIONS);
        assertThat(request.userContext()).contains("<dados_do_usuario>").contains("Alimentação");
        assertThat(request.tools()).extracting(t -> t.name())
            .contains("propose_create_transaction", "propose_update_transaction", "propose_delete_transaction",
                "propose_create_goal", "propose_goal_contribution", "search_transactions", "list_goals");
        assertThat(request.history()).last().extracting(m -> m.content()).isEqualTo("oi");
    }

    @Test
    void monthlyLimitCountsOnlyUserMessagesAndSurvivesHistoryDeletion() throws Exception {
        Session session = register();
        when(aiChatProvider.chat(any(), any())).thenReturn(new AiChatResult("ok", 1, 1, false));

        sendChat(session, "1").andExpect(status().isOk()).andExpect(jsonPath("$.messagesUsedThisMonth").value(1));
        sendChat(session, "2").andExpect(status().isOk()).andExpect(jsonPath("$.messagesUsedThisMonth").value(2));

        // Apagar o histórico não devolve mensagens
        mockMvc.perform(delete("/api/v1/chat/history").header("Authorization", bearer(session)))
            .andExpect(status().isOk());

        sendChat(session, "3").andExpect(status().isOk()).andExpect(jsonPath("$.messagesUsedThisMonth").value(3));
        sendChat(session, "4").andExpect(status().isPaymentRequired());

        mockMvc.perform(get("/api/v1/chat/stats").header("Authorization", bearer(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messagesUsedThisMonth").value(3))
            .andExpect(jsonPath("$.monthlyLimit").value(3))
            .andExpect(jsonPath("$.remainingThisMonth").value(0))
            .andExpect(jsonPath("$.plan").value("FREE"));
    }

    @Test
    void failedAiCallDoesNotConsumeTheLimit() throws Exception {
        Session session = register();
        when(aiChatProvider.chat(any(), any())).thenThrow(new AiProviderException("fora do ar"));

        sendChat(session, "oi").andExpect(status().isServiceUnavailable());

        mockMvc.perform(get("/api/v1/chat/stats").header("Authorization", bearer(session)))
            .andExpect(jsonPath("$.messagesUsedThisMonth").value(0));
        mockMvc.perform(get("/api/v1/chat/history").header("Authorization", bearer(session)))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void toolsCannotTouchAnotherUsersData() throws Exception {
        Session owner = register();
        Session attacker = register();

        // Dono cria um lançamento
        MvcResult created = mockMvc.perform(post("/api/v1/transactions").header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("amount", 10, "type", "EXPENSE", "categoryId", food.getId().toString(),
                    "description", "Café", "transactionDate", "2026-11-01"))))
            .andExpect(status().isCreated())
            .andReturn();
        String transactionId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        AtomicReference<AiToolResult> deleteResult = new AtomicReference<>();
        when(aiChatProvider.chat(any(), any())).thenAnswer(inv -> {
            AiToolHandler tools = inv.getArgument(1);
            deleteResult.set(tools.handle("propose_delete_transaction", Map.of("transaction_id", transactionId)));
            return new AiChatResult("ok", 1, 1, false);
        });

        sendChat(attacker, "apaga o lançamento " + transactionId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.proposedActions.length()").value(0));
        assertThat(deleteResult.get().error()).isTrue();
        assertThat(deleteResult.get().content()).contains("não encontrado");
    }

    @Test
    void invalidToolInputReturnsErrorToTheModel() throws Exception {
        Session session = register();
        AtomicReference<AiToolResult> result = new AtomicReference<>();
        when(aiChatProvider.chat(any(), any())).thenAnswer(inv -> {
            AiToolHandler tools = inv.getArgument(1);
            result.set(tools.handle("propose_create_transaction", Map.of(
                "description", "X", "amount", -5, "type", "EXPENSE", "category_name", "Alimentação")));
            return new AiChatResult("ok", 1, 1, false);
        });

        sendChat(session, "gastei -5").andExpect(jsonPath("$.proposedActions.length()").value(0));
        assertThat(result.get().error()).isTrue();
    }

    @Test
    void refusalReturnsFriendlyMessage() throws Exception {
        Session session = register();
        when(aiChatProvider.chat(any(), any())).thenReturn(new AiChatResult(null, 1, 0, true));
        sendChat(session, "algo proibido")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Não posso ajudar")));
    }

    private org.springframework.test.web.servlet.ResultActions sendChat(Session session, String message) throws Exception {
        return mockMvc.perform(post("/api/v1/chat/messages").header("Authorization", bearer(session))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("message", message))));
    }
}
