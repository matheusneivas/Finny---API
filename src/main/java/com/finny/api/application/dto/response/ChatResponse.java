package com.finny.api.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private UUID messageId;
    private String reply;
    private Integer tokensUsed;
    /** Ações aguardando confirmação do usuário (vazio quando não há). */
    @Builder.Default
    private List<ProposedActionResponse> proposedActions = List.of();
    private Long messagesUsedThisMonth;
    private Long monthlyLimit;
}
