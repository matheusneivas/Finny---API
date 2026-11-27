package com.finny.api.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finny.api.application.dto.request.ChatMessageRequest;
import com.finny.api.application.dto.response.ChatHistoryResponse;
import com.finny.api.application.dto.response.ChatResponse;
import com.finny.api.application.dto.response.ChatStatsResponse;
import com.finny.api.application.dto.response.ProposedActionResponse;
import com.finny.api.application.exception.BusinessException;
import com.finny.api.application.exception.PlanLimitException;
import com.finny.api.application.exception.EntityNotFoundException;
import com.finny.api.application.exception.ServiceUnavailableException;
import com.finny.api.application.service.assistant.AssistantPromptBuilder;
import com.finny.api.application.service.assistant.AssistantToolbox;
import com.finny.api.application.service.assistant.ChatProperties;
import com.finny.api.application.service.assistant.ChatUsageService;
import com.finny.api.application.service.assistant.ChatUsageService.MonthlyUsage;
import com.finny.api.domain.entity.ChatMessage;
import com.finny.api.domain.entity.User;
import com.finny.api.domain.enums.MessageRole;
import com.finny.api.domain.enums.ProposedActionStatus;
import com.finny.api.domain.enums.UserPlan;
import com.finny.api.domain.repository.ChatMessageRepository;
import com.finny.api.domain.repository.UserRepository;
import com.finny.api.infrastructure.ai.AiChatProvider;
import com.finny.api.infrastructure.ai.AiChatRequest;
import com.finny.api.infrastructure.ai.AiChatResult;
import com.finny.api.infrastructure.ai.AiMessage;
import com.finny.api.infrastructure.ai.AiProviderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chat com o assistente.
 * <p>
 * A chamada à IA acontece FORA de transação de banco (pode levar vários segundos);
 * as leituras e gravações usam transações curtas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private static final TypeReference<List<ProposedActionResponse>> ACTIONS_TYPE = new TypeReference<>() {};
    private static final String REFUSAL_REPLY =
        "Não posso ajudar com esse pedido. Posso ajudar com seus gastos, receitas e metas? 🙂";

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ChatActionService chatActionService;
    private final AiChatProvider aiChatProvider;
    private final AssistantPromptBuilder promptBuilder;
    private final AssistantToolbox toolbox;
    private final ChatUsageService usageService;
    private final ChatProperties chatProperties;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final PlanService planService;

    public ChatResponse sendMessage(UUID userId, ChatMessageRequest request) {
        String text = request.getMessage().trim();
        log.info("Mensagem recebida | user: {} | tamanho: {}", userId, text.length());

        // 1) Reserva uma mensagem do limite mensal (atômico) + grava a mensagem + monta contexto
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));
        UserPlan plan = planService.effectivePlan(user);
        MonthlyUsage usage = usageService.tryReserve(userId, plan);
        if (usage == null) {
            long limit = usageService.limitFor(plan);
            log.warn("Limite mensal atingido | user: {} | limite: {}", userId, limit);
            if (plan == UserPlan.FREE) throw new PlanLimitException(limitMessage(plan, limit));
            throw new BusinessException(limitMessage(plan, limit));
        }

        Prepared prepared;
        try {
            prepared = transactionTemplate.execute(status -> {
                User managed = userRepository.getReferenceById(userId);
                List<AiMessage> history = loadHistory(userId);

                ChatMessage userMessage = new ChatMessage();
                userMessage.setUser(managed);
                userMessage.setRole(MessageRole.USER);
                userMessage.setContent(text);
                userMessage = chatMessageRepository.save(userMessage);

                history.add(AiMessage.user(text));
                return new Prepared(userMessage.getId(), promptBuilder.buildUserContext(user),
                    normalizeHistory(history));
            });
        } catch (RuntimeException e) {
            usageService.release(userId);
            throw e;
        }

        // 2) Chamada à IA (sem transação aberta)
        AssistantToolbox.Session tools = toolbox.newSession(userId);
        AiChatResult result;
        try {
            result = aiChatProvider.chat(
                new AiChatRequest(AssistantPromptBuilder.STATIC_INSTRUCTIONS, prepared.userContext(),
                    prepared.history(), toolbox.definitions()),
                tools);
        } catch (AiProviderException e) {
            // A mensagem não conta no limite se a IA falhou
            chatMessageRepository.deleteById(prepared.userMessageId());
            usageService.release(userId);
            log.error("IA indisponível | user: {} | provedor: {}", userId, aiChatProvider.name());
            throw new ServiceUnavailableException("O assistente está indisponível no momento. Tente novamente em instantes.");
        }

        List<ProposedActionResponse> actions = result.refused() ? List.of() : tools.proposals();
        String reply;
        if (result.refused()) {
            reply = REFUSAL_REPLY;
        } else if (StringUtils.hasText(result.reply())) {
            reply = result.reply();
        } else if (!actions.isEmpty()) {
            reply = "Preparei o que você pediu. Confira e confirme no cartão abaixo.";
        } else {
            reply = "Não entendi muito bem. Pode reformular?";
        }

        // 3) Grava a resposta
        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setUser(userRepository.getReferenceById(userId));
        assistantMessage.setRole(MessageRole.ASSISTANT);
        assistantMessage.setContent(reply);
        assistantMessage.setTokensUsed(result.totalTokens());
        assistantMessage.setProposedActions(actions.isEmpty() ? null : writeActions(actions));
        assistantMessage = chatMessageRepository.save(assistantMessage);
        if (!actions.isEmpty()) {
            chatActionService.registerPending(userId, assistantMessage.getId(), actions);
        }

        log.info("Resposta enviada | user: {} | tokens: {} | propostas: {}", userId, result.totalTokens(), actions.size());

        return ChatResponse.builder()
            .messageId(assistantMessage.getId())
            .reply(reply)
            .tokensUsed(result.totalTokens())
            .proposedActions(actions)
            .messagesUsedThisMonth(usage.used())
            .monthlyLimit(usage.limit())
            .build();
    }

    @Transactional(readOnly = true)
    public Page<ChatHistoryResponse> getHistory(UUID userId, Pageable pageable) {
        Page<ChatMessage> page = chatMessageRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        Map<String, ProposedActionStatus> statuses = chatActionService.statusesFor(
            page.getContent().stream().filter(m -> m.getProposedActions() != null).map(ChatMessage::getId).toList());
        return page.map(m -> toHistoryResponse(m, statuses));
    }

    @Transactional
    public void deleteHistory(UUID userId) {
        log.info("Apagando histórico do chat | user: {}", userId);
        chatActionService.deleteAllForUser(userId);
        chatMessageRepository.deleteByUserId(userId);
    }

    @Transactional(readOnly = true)
    public ChatStatsResponse getStats(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));
        UserPlan plan = planService.effectivePlan(user);
        MonthlyUsage usage = usageService.getMonthlyUsage(userId, plan);
        return ChatStatsResponse.builder()
            .plan(plan)
            .messagesUsedThisMonth(usage.used())
            .monthlyLimit(usage.limit())
            .remainingThisMonth(usage.remaining())
            .resetsAt(usage.resetsAt().toOffsetDateTime())
            .totalTokensUsed(chatMessageRepository.sumTokensUsedByUserId(userId))
            .build();
    }

    // ── Auxiliares ──────────────────────────────────────────────────────────

    private List<AiMessage> loadHistory(UUID userId) {
        List<ChatMessage> recent = chatMessageRepository
            .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, chatProperties.getHistorySize()))
            .getContent();
        Map<String, ProposedActionStatus> statuses = chatActionService.statusesFor(
            recent.stream().filter(m -> m.getProposedActions() != null).map(ChatMessage::getId).toList());
        List<AiMessage> history = new ArrayList<>();
        for (int i = recent.size() - 1; i >= 0; i--) {
            ChatMessage m = recent.get(i);
            String content = m.getContent();
            if (m.getRole() == MessageRole.ASSISTANT && m.getProposedActions() != null) {
                content += "\n[Ações propostas nesta resposta (situação do cartão): "
                    + summaries(m.getProposedActions(), statuses) + ". Confira nos dados atuais antes de propor de novo.]";
            }
            history.add(m.getRole() == MessageRole.USER ? AiMessage.user(content) : AiMessage.assistant(content));
        }
        return history;
    }

    /** Garante que a conversa começa pelo usuário e alterna os papéis (junta mensagens seguidas). */
    static List<AiMessage> normalizeHistory(List<AiMessage> raw) {
        List<AiMessage> result = new ArrayList<>();
        for (AiMessage message : raw) {
            if (!StringUtils.hasText(message.content())) continue;
            if (result.isEmpty() && message.role() != AiMessage.Role.USER) continue;
            if (!result.isEmpty() && result.get(result.size() - 1).role() == message.role()) {
                AiMessage last = result.remove(result.size() - 1);
                result.add(new AiMessage(last.role(), last.content() + "\n\n" + message.content()));
            } else {
                result.add(message);
            }
        }
        return result;
    }

    private ChatHistoryResponse toHistoryResponse(ChatMessage m, Map<String, ProposedActionStatus> statuses) {
        List<ProposedActionResponse> actions = readActions(m.getProposedActions());
        actions.forEach(a -> a.setStatus(statuses.getOrDefault(a.getId(), ProposedActionStatus.PENDING)));
        return ChatHistoryResponse.builder()
            .id(m.getId())
            .role(m.getRole())
            .content(m.getContent())
            .tokensUsed(m.getTokensUsed())
            .createdAt(m.getCreatedAt())
            .proposedActions(actions)
            .build();
    }

    private String summaries(String json, Map<String, ProposedActionStatus> statuses) {
        return String.join("; ", readActions(json).stream()
            .map(a -> a.getSummary() + " (" + statusLabel(statuses.getOrDefault(a.getId(), ProposedActionStatus.PENDING)) + ")")
            .toList());
    }

    private static String statusLabel(ProposedActionStatus status) {
        return switch (status) {
            case CONFIRMED -> "confirmada pelo usuário";
            case DISCARDED -> "descartada pelo usuário";
            case PENDING -> "aguardando confirmação";
        };
    }

    private String writeActions(List<ProposedActionResponse> actions) {
        try {
            return objectMapper.writeValueAsString(actions);
        } catch (JsonProcessingException e) {
            log.warn("Falha ao serializar propostas", e);
            return null;
        }
    }

    private List<ProposedActionResponse> readActions(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, ACTIONS_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("Propostas inválidas no histórico: {}", e.getMessage());
            return List.of();
        }
    }

    private static String limitMessage(UserPlan plan, long limit) {
        return plan == UserPlan.FREE
            ? String.format("Você usou as %d mensagens com a IA do plano grátis deste mês. O limite renova no dia 1º, "
                + "ou assine o Premium.", limit)
            : String.format("Você atingiu o limite de %d mensagens deste mês. O limite renova no dia 1º.", limit);
    }

    private record Prepared(UUID userMessageId, String userContext, List<AiMessage> history) {}
}
