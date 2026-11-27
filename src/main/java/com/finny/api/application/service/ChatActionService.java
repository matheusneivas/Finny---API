package com.finny.api.application.service;

import com.finny.api.application.dto.response.ProposedActionResponse;
import com.finny.api.application.exception.EntityNotFoundException;
import com.finny.api.application.exception.ResourceConflictException;
import com.finny.api.application.exception.ValidationException;
import com.finny.api.domain.entity.ChatAction;
import com.finny.api.domain.enums.ProposedActionStatus;
import com.finny.api.domain.repository.ChatActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Estado dos cartões de confirmação: PENDING até o usuário confirmar ou descartar.
 * CONFIRMED é definitivo (o dado já foi gravado); DISCARDED ainda pode virar CONFIRMED.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatActionService {

    private final ChatActionRepository repository;
    private final Clock clock;

    @Transactional
    public void registerPending(UUID userId, UUID messageId, List<ProposedActionResponse> actions) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<ChatAction> rows = actions.stream().map(a -> {
            ChatAction row = new ChatAction();
            row.setId(a.getId());
            row.setUserId(userId);
            row.setMessageId(messageId);
            row.setStatus(ProposedActionStatus.PENDING);
            row.setUpdatedAt(now);
            return row;
        }).toList();
        repository.saveAll(rows);
    }

    /** Estados por id de ação, para as mensagens informadas. Ação sem registro conta como PENDING. */
    @Transactional(readOnly = true)
    public Map<String, ProposedActionStatus> statusesFor(Collection<UUID> messageIds) {
        if (messageIds.isEmpty()) return Map.of();
        return repository.findByMessageIdIn(messageIds).stream()
            .collect(Collectors.toMap(ChatAction::getId, ChatAction::getStatus));
    }

    @Transactional
    public void updateStatus(UUID userId, String actionId, ProposedActionStatus status) {
        if (status == null || status == ProposedActionStatus.PENDING) {
            throw new ValidationException("Status deve ser CONFIRMED ou DISCARDED");
        }
        if (actionId == null || actionId.length() > 64) {
            throw new EntityNotFoundException("Ação não encontrada");
        }
        ChatAction action = repository.findByIdAndUserId(actionId, userId)
            .orElseThrow(() -> new EntityNotFoundException("Ação não encontrada"));
        apply(action, status);
    }

    /**
     * Chamado quando uma criação com Idempotency-Key igual ao id de um cartão deu certo: o cartão
     * fica CONFIRMED mesmo que o app não consiga avisar depois.
     */
    @Transactional
    public void markConfirmedIfAction(UUID userId, String actionId) {
        repository.findByIdAndUserId(actionId, userId)
            .filter(a -> a.getStatus() != ProposedActionStatus.CONFIRMED)
            .ifPresent(a -> apply(a, ProposedActionStatus.CONFIRMED));
    }

    @Transactional
    public void deleteAllForUser(UUID userId) {
        repository.deleteByUserId(userId);
    }

    private void apply(ChatAction action, ProposedActionStatus status) {
        if (action.getStatus() == status) return; // idempotente
        if (action.getStatus() == ProposedActionStatus.CONFIRMED) {
            throw new ResourceConflictException("Esta ação já foi confirmada e não pode ser descartada");
        }
        action.setStatus(status);
        action.setUpdatedAt(OffsetDateTime.now(clock));
        repository.save(action);
        log.info("Cartão do chat atualizado | user: {} | ação: {} | status: {}", action.getUserId(), action.getId(), status);
    }
}
