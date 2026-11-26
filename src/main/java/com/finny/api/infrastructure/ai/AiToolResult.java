package com.finny.api.infrastructure.ai;

/**
 * @param content conteúdo (JSON) devolvido ao modelo
 * @param error   true quando a chamada falhou (o modelo pode corrigir e tentar de novo)
 */
public record AiToolResult(String content, boolean error) {

    public static AiToolResult ok(String content) {
        return new AiToolResult(content, false);
    }

    public static AiToolResult error(String content) {
        return new AiToolResult(content, true);
    }
}
