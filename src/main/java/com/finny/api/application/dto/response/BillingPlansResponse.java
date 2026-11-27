package com.finny.api.application.dto.response;

import java.math.BigDecimal;

/**
 * @param enabled false quando os pagamentos ainda não foram ligados no servidor (esconda o botão de assinar)
 */
public record BillingPlansResponse(
    boolean enabled,
    BigDecimal monthlyPrice,
    int trialDays,
    int pixDays,
    Free free,
    long premiumChatMessagesPerMonth
) {
    public record Free(long transactionsPerMonth, long activeGoals, long chatMessagesPerMonth) {}
}
