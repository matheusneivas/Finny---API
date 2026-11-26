package com.finny.api.infrastructure.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuração da IA (prefixo {@code ai.*}). Tudo pode ser trocado por variáveis de ambiente:
 * AI_PROVIDER, AI_MODEL, AI_API_KEY, AI_MAX_TOKENS, AI_EFFORT...
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** "anthropic" (padrão) ou "openai". */
    private String provider = "anthropic";

    /** Modelo usado pelo provedor. Padrão: Claude Haiku 5.5 (rápido e barato para chat). */
    private String model = "claude-haiku-5-5";

    /** Chave da API do provedor. Nunca versionar: use a variável AI_API_KEY. */
    private String apiKey = "";

    /** URL base opcional (proxy, gateway corporativo ou testes). */
    private String baseUrl = "";

    /** Limite de tokens de saída por chamada. */
    private long maxTokens = 4096;

    /** Esforço de raciocínio (Claude): low, medium, high. Chat funciona bem em low. */
    private String effort = "low";

    /** Máximo de idas e voltas de ferramentas por mensagem (proteção contra loops). */
    private int maxToolIterations = 5;

    /** Tempo máximo por chamada ao provedor. */
    private Duration timeout = Duration.ofSeconds(60);

    /** Tentativas automáticas em erros temporários (429/5xx). */
    private int maxRetries = 2;
}
