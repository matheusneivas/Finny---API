package com.finny.api.application.dto.response;

import com.finny.api.domain.enums.UserPlan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatStatsResponse {
    private UserPlan plan;
    /** Mensagens enviadas pelo usuário no mês atual (respostas da IA não contam). */
    private Long messagesUsedThisMonth;
    private Long monthlyLimit;
    private Long remainingThisMonth;
    /** Quando o contador zera (início do próximo mês, fuso America/Sao_Paulo). */
    private OffsetDateTime resetsAt;
    private Long totalTokensUsed;
}
