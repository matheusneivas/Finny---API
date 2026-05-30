package com.finny.api.infrastructure.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        return sendMessage(messages, null, null);
    }

    public OpenAiResponse sendMessage(List<ChatMessageDto> messages, List<ToolDefinition> tools) {
        return sendMessage(messages, tools, null);
    }

    public OpenAiResponse sendMessage(List<ChatMessageDto> messages, List<ToolDefinition> tools, String toolChoice) {
        log.info("Enviando {} mensagens para OpenAI | modelo: {} | tool_choice: {}", messages.size(), model, toolChoice);

        OpenAiRequest request = OpenAiRequest.builder()
            .model(model)
            .messages(messages)
            .maxTokens(maxTokens)
            .temperature(temperature)
            .tools(tools != null && !tools.isEmpty() ? tools : null)
            .toolChoice(toolChoice)
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

    public static ToolDefinition createTransactionTool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("description", Map.of("type", "string", "description", "Nome ou descrição do item (ex: PS5, Aluguel, Salário)"));
        properties.put("amount", Map.of("type", "number", "description", "Valor total em reais (ex: 5000.00)"));
        properties.put("type", Map.of("type", "string", "enum", List.of("INCOME", "EXPENSE"), "description", "INCOME para receita, EXPENSE para despesa"));
        properties.put("category_name", Map.of("type", "string", "description", "Nome da categoria que melhor se encaixa (ex: Eletrônicos, Alimentação, Salário)"));
        properties.put("transaction_date", Map.of("type", "string", "description", "Data da transação no formato YYYY-MM-DD. Use a data de hoje se não informada."));
        properties.put("frequency", Map.of("type", "string", "enum", List.of("ONCE", "DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY"), "description", "ONCE para pagamento único, MONTHLY para parcelamento mensal"));
        properties.put("repeat_count", Map.of("type", "integer", "description", "Número de parcelas ou repetições. Use 1 para pagamento único."));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("description", "amount", "type", "category_name", "transaction_date", "frequency", "repeat_count"));

        return ToolDefinition.builder()
            .type("function")
            .function(ToolDefinition.FunctionDefinition.builder()
                .name("create_transaction")
                .description("Cria uma nova transação financeira (despesa ou receita) na planilha do usuário. Use quando o usuário pedir para adicionar, registrar ou lançar uma transação.")
                .parameters(parameters)
                .build())
            .build();
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
            context.getActiveGoals().forEach(goal -> {
                prompt.append("- ").append(goal.getName())
                      .append(": R$ ").append(goal.getSavedAmount())
                      .append(" de R$ ").append(goal.getTargetAmount())
                      .append(" (").append(goal.getProgressPercentage()).append("%)");
                if (goal.getDeadline() != null)
                    prompt.append(" — prazo: ").append(goal.getDeadline());
                prompt.append("\n");
            });
        }

        if (context.getExpensesByCategory() != null && !context.getExpensesByCategory().isEmpty()) {
            prompt.append("\nGASTOS POR CATEGORIA (ÚLTIMOS 30 DIAS):\n");
            context.getExpensesByCategory().forEach(cat ->
                prompt.append("- ").append(cat.getName())
                      .append(": R$ ").append(cat.getTotal())
                      .append(" (").append(cat.getPercentage()).append("%)\n")
            );
        }

        if (context.getRecentTransactions() != null && !context.getRecentTransactions().isEmpty()) {
            prompt.append("\nÚLTIMAS TRANSAÇÕES:\n");
            context.getRecentTransactions().forEach(t -> {
                prompt.append("- ").append(t.getDate())
                      .append(" | ").append(t.getType().equals("INCOME") ? "RECEITA" : "DESPESA")
                      .append(" | ").append(t.getCategory());
                if (t.getDescription() != null && !t.getDescription().isBlank())
                    prompt.append(" | ").append(t.getDescription());
                prompt.append(": R$ ").append(t.getAmount()).append("\n");
            });
        }

        prompt.append("\nINSTRUÇÕES:\n");
        prompt.append("1. Responda sempre em português brasileiro\n");
        prompt.append("2. Seja direto e prático, evite respostas muito longas\n");
        prompt.append("3. Use emojis moderadamente (máximo 2 por resposta)\n");
        prompt.append("4. Quando sugerir ações, seja específico e acionável\n");
        prompt.append("5. Use os dados financeiros reais do usuário quando disponíveis\n");
        prompt.append("6. Incentive bons hábitos financeiros de forma positiva\n");
        prompt.append("7. Nunca invente dados — use apenas as informações fornecidas\n");
        prompt.append("8. O sistema NÃO tem integração com bancos ou contas bancárias. Todos os dados financeiros são registrados manualmente pelo próprio usuário. Nunca sugira que detectou algo automaticamente na conta do usuário, nem mencione dados que ele não registrou. Se os dados parecerem incompletos, incentive o usuário a registrar suas transações para obter análises mais precisas.\n");
        prompt.append("9. REGRA MAIS IMPORTANTE: Sempre que o usuário pedir para ADICIONAR, REGISTRAR, LANÇAR, INCLUIR ou ANOTAR qualquer transação financeira (compra, gasto, despesa, receita, salário, conta, parcela, etc.), você DEVE chamar OBRIGATORIAMENTE a ferramenta create_transaction. Não responda apenas com texto confirmando ou descrevendo — execute a ferramenta na mesma resposta. Exemplos que devem acionar a ferramenta: 'adiciona o PS5', 'registra meu salário', 'anota a conta de luz', 'coloca na planilha', 'lança a compra'.\n");

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

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private List<ToolDefinition> tools;

        @JsonProperty("tool_choice")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String toolChoice;
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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatMessageDto {
        private String role;
        private String content;

        @JsonProperty("tool_calls")
        private List<ToolCall> toolCalls;

        @JsonProperty("tool_call_id")
        private String toolCallId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolDefinition {
        private String type;
        private FunctionDefinition function;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class FunctionDefinition {
            private String name;
            private String description;
            private Map<String, Object> parameters;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        private String id;
        private String type;
        private ToolCallFunction function;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ToolCallFunction {
            private String name;
            private String arguments;
        }
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
        private List<CategoryContext> expensesByCategory;
        private List<TransactionContext> recentTransactions;
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
        private String deadline;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryContext {
        private String name;
        private String total;
        private double percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionContext {
        private String date;
        private String type;
        private String category;
        private String description;
        private String amount;
    }
}
