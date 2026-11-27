package com.finny.api.infrastructure.ai;

/**
 * Abstração do provedor de IA (Claude, OpenAI...).
 * <p>
 * O provedor é responsável pelo loop de ferramentas: sempre que o modelo pedir uma
 * ferramenta, ele chama o {@link AiToolHandler} e devolve o resultado ao modelo,
 * até obter a resposta final em texto.
 */
public interface AiChatProvider {

    AiChatResult chat(AiChatRequest request, AiToolHandler toolHandler);

    /** Nome do provedor, para logs e métricas. */
    String name();
}
