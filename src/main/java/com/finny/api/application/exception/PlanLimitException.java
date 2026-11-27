package com.finny.api.application.exception;

/** Limite do plano grátis atingido (402, code PLAN_LIMIT_REACHED): o app mostra a tela do Premium. */
public class PlanLimitException extends RuntimeException {
    public static final String CODE = "PLAN_LIMIT_REACHED";

    public PlanLimitException(String message) {
        super(message);
    }
}
