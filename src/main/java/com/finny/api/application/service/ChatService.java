package com.finny.api.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finny.api.application.dto.request.ChatMessageRequest;
import com.finny.api.application.dto.request.TransactionRequest;
import com.finny.api.application.dto.response.TransactionResponse;
import com.finny.api.application.dto.response.ChatHistoryResponse;
import com.finny.api.application.dto.response.ChatResponse;
import com.finny.api.application.exception.BusinessException;
import com.finny.api.application.exception.EntityNotFoundException;
import com.finny.api.application.mapper.ChatMessageMapper;
import com.finny.api.domain.entity.Category;
import com.finny.api.domain.entity.ChatMessage;
import com.finny.api.domain.entity.Goal;
import com.finny.api.domain.entity.Transaction;
import com.finny.api.domain.entity.User;
import com.finny.api.domain.enums.ExpenseFrequency;
import com.finny.api.domain.enums.GoalStatus;
import com.finny.api.domain.enums.MessageRole;
import com.finny.api.domain.enums.TransactionType;
import com.finny.api.domain.enums.UserPlan;
import com.finny.api.domain.repository.CategoryRepository;
import com.finny.api.domain.repository.ChatMessageRepository;
import com.finny.api.domain.repository.GoalRepository;
import com.finny.api.domain.repository.TransactionRepository;
import com.finny.api.domain.repository.UserProfileRepository;
import com.finny.api.domain.repository.UserRepository;
import com.finny.api.infrastructure.openai.OpenAiClient;
import com.finny.api.infrastructure.openai.OpenAiClient.CategoryContext;
import com.finny.api.infrastructure.openai.OpenAiClient.ChatMessageDto;
import com.finny.api.infrastructure.openai.OpenAiClient.FinancialSummaryContext;
import com.finny.api.infrastructure.openai.OpenAiClient.GoalContext;
import com.finny.api.infrastructure.openai.OpenAiClient.OpenAiResponse;
import com.finny.api.infrastructure.openai.OpenAiClient.ProfileContext;
import com.finny.api.infrastructure.openai.OpenAiClient.ToolCall;
import com.finny.api.infrastructure.openai.OpenAiClient.ToolDefinition;
import com.finny.api.infrastructure.openai.OpenAiClient.TransactionContext;
import com.finny.api.infrastructure.openai.OpenAiClient.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final GoalRepository goalRepository;
    private final ChatMessageMapper chatMessageMapper;
    private final OpenAiClient openAiClient;
    private final TransactionService transactionService;
    private final ObjectMapper objectMapper;

    private static final long FREE_MESSAGE_LIMIT = 100;
    private static final long PREMIUM_MESSAGE_LIMIT = 1000;

    @Transactional
    public ChatResponse sendMessage(UUID userId, ChatMessageRequest request) {
        log.info("Processando mensagem | user: {} | tamanho: {}", userId, request.getMessage().length());

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        checkMessageLimit(userId, user.getPlan());

        ChatMessage userMessage = new ChatMessage();
        userMessage.setUser(user);
        userMessage.setRole(MessageRole.USER);
        userMessage.setContent(request.getMessage());
        chatMessageRepository.save(userMessage);

        try {
            UserContext context = buildUserContext(user);
            List<ChatMessageDto> messages = buildMessageHistory(userId, request.getMessage(), context);

            List<ToolDefinition> tools = List.of(OpenAiClient.createTransactionTool());
            String toolChoice = isTransactionCreationRequest(request.getMessage()) ? "required" : null;
            OpenAiResponse openAiResponse = openAiClient.sendMessage(messages, tools, toolChoice);

            OpenAiResponse.Choice choice = openAiResponse.getChoices().get(0);
            Integer tokensUsed = openAiResponse.getUsage().getTotalTokens();
            String assistantReply;

            log.info("OpenAI finish_reason: {} | tool_choice_forced: {} | user: {}",
                choice.getFinishReason(), toolChoice != null, userId);

            if ("tool_calls".equals(choice.getFinishReason()) && choice.getMessage().getToolCalls() != null) {
                ToolCall toolCall = choice.getMessage().getToolCalls().get(0);
                assistantReply = executeToolCall(userId, toolCall, messages);
            } else {
                assistantReply = choice.getMessage().getContent();
            }

            ChatMessage assistantMessage = new ChatMessage();
            assistantMessage.setUser(user);
            assistantMessage.setRole(MessageRole.ASSISTANT);
            assistantMessage.setContent(assistantReply);
            assistantMessage.setTokensUsed(tokensUsed);
            chatMessageRepository.save(assistantMessage);

            log.info("Mensagem processada | user: {} | tokens: {}", userId, tokensUsed);

            return ChatResponse.builder()
                .reply(assistantReply)
                .tokensUsed(tokensUsed)
                .build();

        } catch (Exception e) {
            log.error("Erro ao processar mensagem | user: {} | erro: {}", userId, e.getMessage(), e);

            ChatMessage errorMessage = new ChatMessage();
            errorMessage.setUser(user);
            errorMessage.setRole(MessageRole.ASSISTANT);
            errorMessage.setContent("Desculpe, estou com dificuldades técnicas no momento. Tente novamente em instantes. 🦉");
            errorMessage.setTokensUsed(0);
            chatMessageRepository.save(errorMessage);

            throw new BusinessException("Erro ao processar mensagem. Tente novamente.");
        }
    }

    @Transactional(readOnly = true)
    public Page<ChatHistoryResponse> getHistory(UUID userId, Pageable pageable) {
        log.debug("Buscando histórico | user: {}", userId);
        Page<ChatMessage> messages = chatMessageRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return messages.map(chatMessageMapper::toResponse);
    }

    @Transactional
    public void deleteHistory(UUID userId) {
        log.info("Deletando histórico | user: {}", userId);
        chatMessageRepository.deleteByUserId(userId);
    }

    private void checkMessageLimit(UUID userId, UserPlan plan) {
        long messageCount = chatMessageRepository.countByUserId(userId);
        long limit = (plan == UserPlan.PREMIUM) ? PREMIUM_MESSAGE_LIMIT : FREE_MESSAGE_LIMIT;

        if (messageCount >= limit) {
            String message = (plan == UserPlan.FREE)
                ? String.format("Você atingiu o limite de %d mensagens gratuitas. Faça upgrade para Premium!", FREE_MESSAGE_LIMIT)
                : String.format("Você atingiu o limite de %d mensagens este mês.", PREMIUM_MESSAGE_LIMIT);

            log.warn("Limite de mensagens atingido | user: {} | count: {} | limit: {}", userId, messageCount, limit);
            throw new BusinessException(message);
        }
    }

    private UserContext buildUserContext(User user) {
        UserContext.UserContextBuilder contextBuilder = UserContext.builder()
            .userName(user.getFullName() != null ? user.getFullName() : "usuário")
            .plan(user.getPlan().toString());

        userProfileRepository.findById(user.getId()).ifPresent(profile ->
            contextBuilder.profile(ProfileContext.builder()
                .incomeRange(profile.getIncomeRange() != null ? profile.getIncomeRange().toString() : "Não informado")
                .knowledgeLevel(profile.getKnowledgeLevel() != null ? profile.getKnowledgeLevel().toString() : "INTERMEDIATE")
                .primaryGoal(profile.getPrimaryGoal() != null ? profile.getPrimaryGoal() : "Organizar finanças")
                .primaryGoalTimeframe(profile.getPrimaryGoalTimeframe())
                .communicationStyle(profile.getCommunicationStyle() != null ? profile.getCommunicationStyle().toString() : "FRIENDLY")
                .paysRent(profile.getPaysRent())
                .monthlyRent(profile.getMonthlyRent() != null ? formatMoney(profile.getMonthlyRent()) : null)
                .hasDebts(profile.getHasDebts())
                .totalDebt(profile.getTotalDebt() != null ? formatMoney(profile.getTotalDebt()) : null)
                .hasInvestments(profile.getHasInvestments())
                .monthlyInvestmentGoal(profile.getMonthlyInvestmentGoal() != null ? formatMoney(profile.getMonthlyInvestmentGoal()) : null)
                .hasEmergencyFund(profile.getHasEmergencyFund())
                .emergencyFundMonths(profile.getEmergencyFundMonths())
                .shortTermGoals(profile.getShortTermGoals() != null ? new java.util.ArrayList<>(profile.getShortTermGoals()) : null)
                .longTermGoals(profile.getLongTermGoals() != null ? new java.util.ArrayList<>(profile.getLongTermGoals()) : null)
                .build())
        );

        LocalDate startDate = LocalDate.now().minusDays(30);
        LocalDate endDate = LocalDate.now();
        List<BigDecimal> summary = transactionRepository.calculateSummary(user.getId(), startDate, endDate);

        if (summary != null && summary.size() == 2) {
            BigDecimal totalIncome = summary.get(0);
            BigDecimal totalExpenses = summary.get(1);
            contextBuilder.financialSummary(FinancialSummaryContext.builder()
                .totalIncome(formatMoney(totalIncome))
                .totalExpenses(formatMoney(totalExpenses))
                .balance(formatMoney(totalIncome.subtract(totalExpenses)))
                .build());
        }

        List<Goal> activeGoals = goalRepository.findByUserIdAndStatusOrderByDeadlineAsc(user.getId(), GoalStatus.ACTIVE);
        if (!activeGoals.isEmpty()) {
            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            List<GoalContext> goalContexts = activeGoals.stream()
                .map(goal -> {
                    BigDecimal progress = goal.getTargetAmount().compareTo(BigDecimal.ZERO) == 0
                        ? BigDecimal.ZERO
                        : goal.getSavedAmount()
                            .divide(goal.getTargetAmount(), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(0, RoundingMode.HALF_UP);

                    return GoalContext.builder()
                        .name(goal.getName())
                        .savedAmount(formatMoney(goal.getSavedAmount()))
                        .targetAmount(formatMoney(goal.getTargetAmount()))
                        .progressPercentage(progress.toString())
                        .deadline(goal.getDeadline() != null ? goal.getDeadline().format(dateFmt) : null)
                        .build();
                })
                .collect(Collectors.toList());

            contextBuilder.activeGoals(goalContexts);
        }

        // Gastos por categoria (top 5)
        List<Object[]> categoryRows = transactionRepository.sumExpenseGroupedByCategory(user.getId(), startDate, endDate);
        if (!categoryRows.isEmpty()) {
            BigDecimal totalExp = categoryRows.stream()
                .map(r -> (BigDecimal) r[4])
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<CategoryContext> categories = categoryRows.stream()
                .limit(5)
                .map(r -> {
                    BigDecimal total = (BigDecimal) r[4];
                    double pct = totalExp.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                        total.divide(totalExp, 4, RoundingMode.HALF_UP)
                             .multiply(BigDecimal.valueOf(100))
                             .doubleValue();
                    return CategoryContext.builder()
                        .name((String) r[1])
                        .total(formatMoney(total))
                        .percentage(Math.round(pct * 10.0) / 10.0)
                        .build();
                })
                .collect(Collectors.toList());

            contextBuilder.expensesByCategory(categories);
        }

        // Últimas 7 transações
        Page<Transaction> recentPage = transactionRepository
            .findByUserIdAndDeletedFalseOrderByTransactionDateDesc(user.getId(), PageRequest.of(0, 7));
        if (!recentPage.isEmpty()) {
            DateTimeFormatter txDateFmt = DateTimeFormatter.ofPattern("dd/MM");
            List<TransactionContext> recent = recentPage.getContent().stream()
                .map(t -> TransactionContext.builder()
                    .date(t.getTransactionDate().format(txDateFmt))
                    .type(t.getType().name())
                    .category(t.getCategory() != null ? t.getCategory().getName() : "Sem categoria")
                    .description(t.getDescription())
                    .amount(formatMoney(t.getAmount()))
                    .build())
                .collect(Collectors.toList());

            contextBuilder.recentTransactions(recent);
        }

        return contextBuilder.build();
    }

    private List<ChatMessageDto> buildMessageHistory(UUID userId, String currentMessage, UserContext context) {
        List<ChatMessageDto> messages = new ArrayList<>();

        messages.add(ChatMessageDto.builder()
            .role("system")
            .content(OpenAiClient.buildSystemPrompt(context))
            .build());

        Pageable pageable = PageRequest.of(0, 10);
        Page<ChatMessage> recentMessages = chatMessageRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        List<ChatMessage> historyList = recentMessages.getContent();
        for (int i = historyList.size() - 1; i >= 0; i--) {
            ChatMessage msg = historyList.get(i);
            messages.add(ChatMessageDto.builder()
                .role(msg.getRole() == MessageRole.USER ? "user" : "assistant")
                .content(msg.getContent())
                .build());
        }

        messages.add(ChatMessageDto.builder()
            .role("user")
            .content(currentMessage)
            .build());

        log.debug("Histórico montado: {} mensagens", messages.size());
        return messages;
    }

    private String executeToolCall(UUID userId, ToolCall toolCall, List<ChatMessageDto> messages) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = objectMapper.readValue(toolCall.getFunction().getArguments(), Map.class);

            BigDecimal amount = new BigDecimal(args.get("amount").toString());
            String type = (String) args.get("type");
            String description = (String) args.get("description");
            String categoryName = (String) args.get("category_name");
            String dateStr = args.containsKey("transaction_date") && args.get("transaction_date") != null
                ? (String) args.get("transaction_date")
                : LocalDate.now().toString();
            String frequency = args.containsKey("frequency") && args.get("frequency") != null
                ? (String) args.get("frequency")
                : "ONCE";
            int repeatCount = args.containsKey("repeat_count") && args.get("repeat_count") != null
                ? ((Number) args.get("repeat_count")).intValue()
                : 1;

            Category category = findBestCategory(userId, categoryName);
            if (category == null) {
                return "Não encontrei uma categoria adequada para registrar essa transação. Pode tentar especificar a categoria?";
            }

            TransactionRequest txRequest = new TransactionRequest();
            txRequest.setAmount(amount);
            txRequest.setType(TransactionType.valueOf(type));
            txRequest.setCategoryId(category.getId());
            txRequest.setDescription(description);
            txRequest.setTransactionDate(LocalDate.parse(dateStr));
            txRequest.setFrequency(ExpenseFrequency.valueOf(frequency));
            txRequest.setRepeatCount(repeatCount);

            TransactionResponse result = transactionService.createTransaction(userId, txRequest);
            log.info("Transação criada via IA | user: {} | id: {}", userId, result.getId());

            String toolResult = String.format(
                "{\"success\": true, \"id\": \"%s\", \"description\": \"%s\", \"amount\": %s, \"category\": \"%s\", \"repeat_count\": %d, \"frequency\": \"%s\"}",
                result.getId(), description, amount.toPlainString(), category.getName(), repeatCount, frequency
            );

            messages.add(ChatMessageDto.builder()
                .role("assistant")
                .toolCalls(List.of(toolCall))
                .build());

            messages.add(ChatMessageDto.builder()
                .role("tool")
                .toolCallId(toolCall.getId())
                .content(toolResult)
                .build());

            OpenAiResponse confirmResponse = openAiClient.sendMessage(messages, null);
            return confirmResponse.getChoices().get(0).getMessage().getContent();

        } catch (Exception e) {
            log.error("Erro ao executar tool call | user: {} | erro: {}", userId, e.getMessage(), e);
            try {
                messages.add(ChatMessageDto.builder()
                    .role("assistant")
                    .toolCalls(List.of(toolCall))
                    .build());
                messages.add(ChatMessageDto.builder()
                    .role("tool")
                    .toolCallId(toolCall.getId())
                    .content("{\"success\": false, \"error\": \"" + e.getMessage() + "\"}")
                    .build());
                OpenAiResponse errResponse = openAiClient.sendMessage(messages, null);
                return errResponse.getChoices().get(0).getMessage().getContent();
            } catch (Exception ex) {
                return "Não consegui adicionar a transação. Verifique os dados e tente novamente.";
            }
        }
    }

    private boolean isTransactionCreationRequest(String message) {
        String lower = message.toLowerCase();
        String[] keywords = {
            "adiciona", "adicione", "adicionar",
            "registra", "registre", "registrar",
            "lança", "lance", "lançar",
            "coloca", "coloque", "colocar",
            "anota", "anote", "anotar",
            "insere", "insira", "inserir",
            "inclui", "inclua", "incluir",
            "na planilha", "no app",
            "comprei", "gastei", "paguei", "recebi",
            "gostaria de registrar", "quero registrar",
            "quero adicionar", "pode adicionar"
        };
        for (String kw : keywords) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    private Category findBestCategory(UUID userId, String categoryName) {
        List<Category> available = categoryRepository.findAllAvailableForUser(userId);
        if (available.isEmpty()) return null;

        String lower = categoryName.toLowerCase();

        return available.stream()
            .filter(c -> c.getName().equalsIgnoreCase(categoryName))
            .findFirst()
            .or(() -> available.stream()
                .filter(c -> c.getName().toLowerCase().contains(lower)
                          || lower.contains(c.getName().toLowerCase()))
                .findFirst())
            .or(() -> available.stream()
                .filter(c -> "outros".equalsIgnoreCase(c.getName()))
                .findFirst())
            .orElse(available.get(0));
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) return "0,00";
        return String.format("%,.2f", value).replace(",", "X").replace(".", ",").replace("X", ".");
    }
}
