package com.finny.api.application.service.assistant;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.chat")
public class ChatProperties {

    /** Mensagens do usuário por mês no plano FREE. */
    private long freeMonthlyLimit = 100;

    /** Mensagens do usuário por mês no plano PREMIUM. */
    private long premiumMonthlyLimit = 1000;

    /** Fuso usado para "mês atual" e "hoje". */
    private ZoneId timezone = ZoneId.of("America/Sao_Paulo");

    /** Quantas mensagens anteriores vão como contexto para a IA. */
    private int historySize = 12;
}
