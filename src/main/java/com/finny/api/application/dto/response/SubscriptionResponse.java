package com.finny.api.application.dto.response;

import com.finny.api.domain.enums.SubscriptionStatus;
import com.finny.api.domain.enums.UserPlan;

import java.time.LocalDateTime;

/**
 * @param plan           plano que vale agora (FREE quando o Premium venceu)
 * @param premiumUntil   até quando o Premium vale (null = grátis, ou Premium sem prazo)
 * @param trialAvailable se ainda pode usar o teste grátis
 * @param card           assinatura no cartão (null = nunca assinou no cartão)
 * @param usage          uso do plano grátis neste mês
 */
public record SubscriptionResponse(
    UserPlan plan,
    LocalDateTime premiumUntil,
    boolean trialAvailable,
    Card card,
    Usage usage
) {
    /** @param checkoutUrl página do Mercado Pago para cadastrar o cartão (só enquanto PENDING) */
    public record Card(SubscriptionStatus status, String checkoutUrl) {}

    public record Usage(long transactionsThisMonth, long activeGoals) {}
}
