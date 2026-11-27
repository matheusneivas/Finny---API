package com.finny.api.infrastructure.ai;

import java.util.List;

/**
 * @param staticInstructions instruções fixas (iguais para todos os usuários; podem ser cacheadas)
 * @param userContext        contexto dinâmico do usuário (dados financeiros, data de hoje...)
 * @param history            conversa em ordem cronológica, terminando na mensagem atual do usuário
 * @param tools              ferramentas disponíveis para o modelo
 */
public record AiChatRequest(
    String staticInstructions,
    String userContext,
    List<AiMessage> history,
    List<AiToolDefinition> tools
) {}
