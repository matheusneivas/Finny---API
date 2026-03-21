package com.finny.api.infrastructure.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;

@Component
@Slf4j
public class OpenAiClient {

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final Integer maxTokens;
    private final Double temperature;

    public OpenAiClient(
        @Value("${openai.api-key}") String apiKey,
        @Value("${openai.api-url}") String apiUrl,
        @Value("${openai.model}") String model,
        @Value("${openai.max-tokens}") Integer maxTokens,
        @Value("${openai.temperature}") Double temperature
    ) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.restTemplate = new RestTemplate();

        log.info("OpenAI Client iniciado com modelo: {}", model);
    }

    public OpenAiResponse sendMessage(List<ChatMessageDto> messages) {
        log.info("Enviando {} mensagens para OpenAI (modelo: {})", messages.size(), model);

        OpenAiRequest request = OpenAiRequest.builder()
            .model(model)
            .messages(messages)
            .maxTokens(maxTokens)
            .temperature(temperature)
            .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<OpenAiRequest> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<OpenAiResponse> response = restTemplate.postForEntity(apiUrl, entity, OpenAiResponse.class);
            OpenAiResponse body = response.getBody();
            log.info("Resposta OpenAI recebida | tokens: {}", body.getUsage().getTotalTokens());
            return body;
        } catch (Exception e) {
            log.error("Erro ao chamar OpenAI API: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao comunicar com IA: " + e.getMessage(), e);
        }
    }

    public static String buildSystemPrompt(UserContext context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Você é o Finny 🦉, um assistente financeiro pessoal inteligente e amigável.\n\n");

        prompt.append("INFORMAÇÕES DO USUÁRIO:\n");
        prompt.append("- Nome: ").append(context.getUserName()).append("\n");
        prompt.append("- Plano: ").append(context.getPlan()).append("\n");

        if (context.getProfile() != null) {
            ProfileContext p = context.getProfile();
            prompt.append("- Faixa de renda: ").append(p.getIncomeRange()).append("\n");
            prompt.append("- Nível de conhecimento financeiro: ").append(p.getKnowledgeLevel()).append("\n");
            prompt.append("- Objetivo principal: ").append(p.getPrimaryGoal()).append("\n");
            if (p.getPrimaryGoalTimeframe() != null)
                prompt.append("- Prazo do objetivo: ").append(p.getPrimaryGoalTimeframe()).append("\n");
            prompt.append("- Estilo de comunicação preferido: ").append(p.getCommunicationStyle()).append("\n");

            if (Boolean.TRUE.equals(p.getPaysRent()))
                prompt.append("- Paga aluguel: Sim, R$ ").append(p.getMonthlyRent()).append("/mês\n");
            else
                prompt.append("- Paga aluguel: Não\n");

            if (Boolean.TRUE.equals(p.getHasDebts()))
                prompt.append("- Possui dívidas: Sim, total de R$ ").append(p.getTotalDebt()).append("\n");
            else
                prompt.append("- Possui dívidas: Não\n");

            if (Boolean.TRUE.equals(p.getHasInvestments()) && p.getMonthlyInvestmentGoal() != null)
                prompt.append("- Meta de investimento mensal: R$ ").append(p.getMonthlyInvestmentGoal()).append("\n");
            else
                prompt.append("- Investe atualmente: Não\n");

            if (Boolean.TRUE.equals(p.getHasEmergencyFund()) && p.getEmergencyFundMonths() != null)
                prompt.append("- Reserva de emergência: ").append(p.getEmergencyFundMonths()).append(" meses\n");
            else
                prompt.append("- Possui reserva de emergência: Não\n");

            if (p.getShortTermGoals() != null && !p.getShortTermGoals().isEmpty())
                prompt.append("- Objetivos de curto prazo: ").append(String.join(", ", p.getShortTermGoals())).append("\n");
            if (p.getLongTermGoals() != null && !p.getLongTermGoals().isEmpty())
                prompt.append("- Objetivos de longo prazo: ").append(String.join(", ", p.getLongTermGoals())).append("\n");
        }

        if (context.getFinancialSummary() != null) {
            prompt.append("\nRESUMO FINANCEIRO (ÚLTIMOS 30 DIAS):\n");
            prompt.append("- Total de receitas: R$ ").append(context.getFinancialSummary().getTotalIncome()).append("\n");
            prompt.append("- Total de despesas: R$ ").append(context.getFinancialSummary().getTotalExpenses()).append("\n");
            prompt.append("- Saldo: R$ ").append(context.getFinancialSummary().getBalance()).append("\n");
        }

        if (context.getActiveGoals() != null && !context.getActiveGoals().isEmpty()) {
            prompt.append("\nMETAS ATIVAS:\n");
            context.getActiveGoals().forEach(goal ->
                prompt.append("- ").append(goal.getName())
                      .append(": R$ ").append(goal.getSavedAmount())
                      .append(" de R$ ").append(goal.getTargetAmount())
                      .append(" (").append(goal.getProgressPercentage()).append("%)\n")
            );
        }

        prompt.append("\nINSTRUÇÕES:\n");
        prompt.append("1. Responda sempre em português brasileiro\n");
        prompt.append("2. Seja direto e prático, evite respostas muito longas\n");
        prompt.append("3. Use emojis moderadamente (máximo 2 por resposta)\n");
        prompt.append("4. Quando sugerir ações, seja específico e acionável\n");
        prompt.append("5. Use os dados financeiros reais do usuário quando disponíveis\n");
        prompt.append("6. Incentive bons hábitos financeiros de forma positiva\n");
        prompt.append("7. Nunca invente dados — use apenas as informações fornecidas\n");

        return prompt.toString();
    }

    // --- DTOs ---

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenAiRequest {
        private String model;
        private List<ChatMessageDto> messages;

        @JsonProperty("max_tokens")
        private Integer maxTokens;

        private Double temperature;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenAiResponse {
        private String id;
        private String object;
        private Long created;
        private String model;
        private List<Choice> choices;
        private Usage usage;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Choice {
            private Integer index;
            private ChatMessageDto message;

            @JsonProperty("finish_reason")
            private String finishReason;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Usage {
            @JsonProperty("prompt_tokens")
            private Integer promptTokens;

            @JsonProperty("completion_tokens")
            private Integer completionTokens;

            @JsonProperty("total_tokens")
            private Integer totalTokens;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessageDto {
        private String role;
        private String content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserContext {
        private String userName;
        private String plan;
        private ProfileContext profile;
        private FinancialSummaryContext financialSummary;
        private List<GoalContext> activeGoals;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProfileContext {
        private String incomeRange;
        private String knowledgeLevel;
        private String primaryGoal;
        private String primaryGoalTimeframe;
        private String communicationStyle;
        private Boolean paysRent;
        private String monthlyRent;
        private Boolean hasDebts;
        private String totalDebt;
        private Boolean hasInvestments;
        private String monthlyInvestmentGoal;
        private Boolean hasEmergencyFund;
        private Integer emergencyFundMonths;
        private List<String> shortTermGoals;
        private List<String> longTermGoals;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinancialSummaryContext {
        private String totalIncome;
        private String totalExpenses;
        private String balance;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GoalContext {
        private String name;
        private String savedAmount;
        private String targetAmount;
        private String progressPercentage;
    }
}
