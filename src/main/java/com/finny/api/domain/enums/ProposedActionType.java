package com.finny.api.domain.enums;

/**
 * Ações que a IA pode propor. Nenhuma é executada pela IA: o app mostra um cartão
 * de confirmação e, se o usuário aceitar, chama o endpoint REST correspondente.
 */
public enum ProposedActionType {
    /** POST /api/v1/transactions com o payload (TransactionRequest). */
    CREATE_TRANSACTION,
    /** PUT /api/v1/transactions/{transactionId} com o restante do payload (TransactionRequest). */
    UPDATE_TRANSACTION,
    /** DELETE /api/v1/transactions/{transactionId}. */
    DELETE_TRANSACTION,
    /** POST /api/v1/goals com o payload (GoalRequest). */
    CREATE_GOAL,
    /** POST /api/v1/goals/{goalId}/contribute com {amount}. */
    CONTRIBUTE_GOAL
}
