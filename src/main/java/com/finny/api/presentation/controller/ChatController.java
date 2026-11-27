package com.finny.api.presentation.controller;

import com.finny.api.application.dto.request.ChatMessageRequest;
import com.finny.api.application.dto.response.ApiResponse;
import com.finny.api.application.dto.response.ChatHistoryResponse;
import com.finny.api.application.dto.response.ChatResponse;
import com.finny.api.application.dto.response.ChatStatsResponse;
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
@Tag(name = "Chat", description = "Chat com o assistente de IA")
@SecurityRequirement(name = "bearerAuth")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/messages")
    @Operation(summary = "Enviar mensagem para o assistente",
        description = "A IA nunca grava dados sozinha. Quando o usuário pede para registrar algo, a resposta "
            + "traz proposedActions; o app mostra um cartão e, se o usuário confirmar, chama o endpoint REST "
            + "correspondente (POST /transactions, PUT/DELETE /transactions/{id}, POST /goals, "
            + "POST /goals/{id}/contribute). Retorna 422 quando o limite mensal acaba e 503 se a IA estiver fora.")
    public ResponseEntity<ChatResponse> sendMessage(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChatMessageRequest request) {
        return ResponseEntity.ok(chatService.sendMessage(userId(userDetails), request));
    }

    @GetMapping("/history")
    @Operation(summary = "Histórico de mensagens (mais recentes primeiro)")
    public ResponseEntity<Page<ChatHistoryResponse>> getHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(chatService.getHistory(userId(userDetails), pageable));
    }

    @DeleteMapping("/history")
    @Operation(summary = "Apagar histórico de mensagens",
        description = "Não devolve mensagens ao limite mensal: o contador considera as mensagens enviadas.")
    public ResponseEntity<ApiResponse<Void>> deleteHistory(@AuthenticationPrincipal UserDetails userDetails) {
        chatService.deleteHistory(userId(userDetails));
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Histórico apagado com sucesso")
                .build());
    }

    @GetMapping("/stats")
    @Operation(summary = "Uso do chat no mês (mensagens usadas, limite do plano e data de renovação)")
    public ResponseEntity<ChatStatsResponse> getStats(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(chatService.getStats(userId(userDetails)));
    }

    private static UUID userId(UserDetails userDetails) {
        return UUID.fromString(userDetails.getUsername());
    }
}
