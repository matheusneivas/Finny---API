package com.finny.api.application.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Limites do plano grátis. O limite do chat fica em app.chat.free-monthly-limit. */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.plan")
public class PlanLimitsProperties {

    /** Lançamentos criados por mês (inclui repetições/parcelas e os que foram apagados depois). */
    private long freeTransactionsPerMonth = 15;

    /** Metas ativas ao mesmo tempo. */
    private long freeActiveGoals = 1;
}
