package com.finny.api.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Limites por minuto (prefixo app.rate-limit.*). */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    /** Login e cadastro, por IP. */
    private int authPerMinute = 10;

    /** Renovação de token, por IP. */
    private int refreshPerMinute = 30;

    /** Mensagens para a IA, por usuário. */
    private int chatPerMinute = 15;

    /** Envio de imagens (OCR), por usuário. */
    private int uploadPerMinute = 10;

    /** Troca de foto de perfil, por usuário, por HORA. */
    private int avatarPerHour = 10;

    /** Demais rotas da API, por usuário (ou IP, se anônimo). */
    private int defaultPerMinute = 300;
}
