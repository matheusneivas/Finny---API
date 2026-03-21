package com.finny.api.presentation.controller;

import com.finny.api.application.dto.request.ChatMessageRequest;
import com.finny.api.application.dto.response.ApiResponse;
import com.finny.api.application.dto.response.ChatHistoryResponse;
import com.finny.api.application.dto.response.ChatResponse;
import com.finny.api.application.dto.response.ChatStatsResponse;
import com.finny.api.domain.repository.ChatMessageRepository;
import com.finny.api.application.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Endpoints de chat com IA")
@SecurityRequirement(name = "bearerAuth")
public class ChatController {

    private final ChatService chatService;
    private final ChatMessageRepository chatMessageRepository;

    @PostMapping("/messages")
    @Operation(summary = "Enviar mensagem para o assistente")
    public ResponseEntity<ChatResponse> sendMessage(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChatMessageRequest request) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        ChatResponse response = chatService.sendMessage(userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    @Operation(summary = "Obter histórico de mensagens")
    public ResponseEntity<Page<ChatHistoryResponse>> getHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 50) Pageable pageable) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        Page<ChatHistoryResponse> response = chatService.getHistory(userId, pageable);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/history")
    @Operation(summary = "Deletar histórico de mensagens")
    public ResponseEntity<ApiResponse<Void>> deleteHistory(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        chatService.deleteHistory(userId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Histórico deletado com sucesso")
                .build());
    }

    @GetMapping("/stats")
    @Operation(summary = "Estatísticas de uso do chat")
    public ResponseEntity<ChatStatsResponse> getStats(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());

        long totalMessages = chatMessageRepository.countByUserId(userId);
        long totalTokens = chatMessageRepository.sumTokensUsedByUserId(userId);
        double estimatedCost = (totalTokens / 1000.0) * 0.002;

        return ResponseEntity.ok(ChatStatsResponse.builder()
                .totalMessages(totalMessages)
                .totalTokensUsed(totalTokens)
                .estimatedCostUsd(estimatedCost)
                .build());
    }
}
