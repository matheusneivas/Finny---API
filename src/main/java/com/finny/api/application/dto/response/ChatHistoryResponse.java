package com.finny.api.application.dto.response;

import com.finny.api.domain.enums.MessageRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryResponse {
    private UUID id;
    private MessageRole role;
    private String content;
    private Integer tokensUsed;
    private LocalDateTime createdAt;
    /** Propostas feitas nesta resposta da IA (só em mensagens ASSISTANT). */
    @Builder.Default
    private List<ProposedActionResponse> proposedActions = List.of();
}
