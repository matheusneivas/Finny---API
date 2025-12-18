package com.finny.api.application.service;

import com.finny.api.application.dto.request.ChatMessageRequest;
import com.finny.api.application.dto.response.ChatHistoryResponse;
import com.finny.api.application.dto.response.ChatResponse;
import com.finny.api.application.exception.EntityNotFoundException;
import com.finny.api.application.mapper.ChatMessageMapper;
import com.finny.api.domain.entity.ChatMessage;
import com.finny.api.domain.entity.User;
import com.finny.api.domain.enums.MessageRole;
import com.finny.api.domain.repository.ChatMessageRepository;
import com.finny.api.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ChatMessageMapper chatMessageMapper;
    // TODO: Injetar N8nClient quando implementado

    @Transactional
    public ChatResponse sendMessage(UUID userId, ChatMessageRequest request) {
        log.debug("Processing chat message from user: {}", userId);

        // Buscar usuário
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        // Salvar mensagem do usuário
        ChatMessage userMessage = new ChatMessage();
        userMessage.setUser(user);
        userMessage.setRole(MessageRole.USER);
        userMessage.setContent(request.getMessage());
        chatMessageRepository.save(userMessage);

        // TODO: Chamar n8n real
        // Por enquanto, resposta mockada
        String mockReply = String.format(
            "Olá! Sou o Finny 🦉, seu assistente financeiro inteligente. " +
            "Você disse: '%s'. Esta é uma resposta mockada. " +
            "A integração com n8n será implementada em breve.",
            request.getMessage()
        );

        // Salvar resposta do assistente
        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setUser(user);
        assistantMessage.setRole(MessageRole.ASSISTANT);
        assistantMessage.setContent(mockReply);
        assistantMessage.setTokensUsed(0); // TODO: Contar tokens reais
        chatMessageRepository.save(assistantMessage);

        log.info("Chat message processed successfully for user: {}", userId);

        return ChatResponse.builder()
                .reply(mockReply)
                .tokensUsed(0)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<ChatHistoryResponse> getHistory(UUID userId, Pageable pageable) {
        log.debug("Getting chat history for user: {}", userId);

        Page<ChatMessage> messages = chatMessageRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return messages.map(chatMessageMapper::toResponse);
    }

    @Transactional
    public void deleteHistory(UUID userId) {
        log.debug("Deleting chat history for user: {}", userId);

        chatMessageRepository.deleteByUserId(userId);

        log.info("Chat history deleted for user: {}", userId);
    }
}
