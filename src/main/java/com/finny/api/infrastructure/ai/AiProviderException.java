package com.finny.api.infrastructure.ai;

/** Falha ao falar com o provedor de IA (rede, chave inválida, indisponibilidade...). */
public class AiProviderException extends RuntimeException {

    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
