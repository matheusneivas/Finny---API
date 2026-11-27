package com.finny.api.infrastructure.ai;

/**
 * @param reply        texto final do assistente
 * @param inputTokens  tokens de entrada somados em todas as chamadas do loop
 * @param outputTokens tokens de saída somados em todas as chamadas do loop
 * @param refused      true quando o modelo recusou responder (filtros de segurança do provedor)
 */
public record AiChatResult(String reply, long inputTokens, long outputTokens, boolean refused) {

    public int totalTokens() {
        return (int) Math.min(Integer.MAX_VALUE, inputTokens + outputTokens);
    }
}
