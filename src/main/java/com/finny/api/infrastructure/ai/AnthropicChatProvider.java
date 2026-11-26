package com.finny.api.infrastructure.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provedor Claude (Anthropic) usando o SDK oficial em Java.
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "anthropic", matchIfMissing = true)
@Slf4j
public class AnthropicChatProvider implements AiChatProvider {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AiProperties properties;
    private volatile AnthropicClient client;

    public AnthropicChatProvider(AiProperties properties) {
        this.properties = properties;
        log.info("Provedor de IA: Anthropic | modelo: {}", properties.getModel());
    }

    @Override
    public String name() {
        return "anthropic";
    }

    @Override
    public AiChatResult chat(AiChatRequest request, AiToolHandler toolHandler) {
        MessageCreateParams.Builder params = MessageCreateParams.builder()
            .model(properties.getModel())
            .maxTokens(properties.getMaxTokens())
            .systemOfTextBlockParams(List.of(
                // Instruções fixas primeiro (cacheáveis); contexto do usuário depois.
                TextBlockParam.builder()
                    .text(request.staticInstructions())
                    .cacheControl(CacheControlEphemeral.builder().build())
                    .build(),
                TextBlockParam.builder()
                    .text(request.userContext())
                    .build()));

        if (StringUtils.hasText(properties.getEffort())) {
            params.outputConfig(OutputConfig.builder()
                .effort(OutputConfig.Effort.of(properties.getEffort().toLowerCase()))
                .build());
        }

        request.tools().forEach(tool -> params.addTool(toAnthropicTool(tool)));

        for (AiMessage message : request.history()) {
            if (message.role() == AiMessage.Role.USER) {
                params.addUserMessage(message.content());
            } else {
                params.addAssistantMessage(message.content());
            }
        }

        long inputTokens = 0;
        long outputTokens = 0;

        for (int iteration = 0; iteration < properties.getMaxToolIterations(); iteration++) {
            Message response = send(params.build());
            inputTokens += response.usage().inputTokens();
            outputTokens += response.usage().outputTokens();

            StopReason stopReason = response.stopReason().orElse(null);
            if (StopReason.REFUSAL.equals(stopReason)) {
                log.warn("Claude recusou a mensagem | categoria: {}",
                    response.stopDetails().map(d -> String.valueOf(d.category())).orElse("?"));
                return new AiChatResult(null, inputTokens, outputTokens, true);
            }

            List<ToolUseBlock> toolUses = response.content().stream()
                .flatMap(block -> block.toolUse().stream())
                .toList();

            if (!StopReason.TOOL_USE.equals(stopReason) || toolUses.isEmpty()) {
                String text = extractText(response);
                if (!StringUtils.hasText(text) && StopReason.MAX_TOKENS.equals(stopReason)) {
                    text = "Minha resposta ficou longa demais. Pode reformular de forma mais específica?";
                }
                return new AiChatResult(text, inputTokens, outputTokens, false);
            }

            // Devolve a resposta completa (inclui blocos de raciocínio) e os resultados das ferramentas
            params.addMessage(response);
            List<ContentBlockParam> results = new ArrayList<>();
            for (ToolUseBlock toolUse : toolUses) {
                AiToolResult result = runTool(toolHandler, toolUse);
                results.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                    .toolUseId(toolUse.id())
                    .content(result.content())
                    .isError(result.error())
                    .build()));
            }
            params.addUserMessageOfContentBlockParams(results);
        }

        log.warn("Limite de iterações de ferramentas atingido ({})", properties.getMaxToolIterations());
        return new AiChatResult(
            "Não consegui concluir esse pedido agora. Pode tentar dividir em partes menores?",
            inputTokens, outputTokens, false);
    }

    private AiToolResult runTool(AiToolHandler handler, ToolUseBlock toolUse) {
        try {
            Map<String, Object> input = toolUse._input().convert(MAP_TYPE);
            return handler.handle(toolUse.name(), input != null ? input : Map.of());
        } catch (Exception e) {
            log.warn("Falha ao executar ferramenta {}: {}", toolUse.name(), e.getMessage());
            return AiToolResult.error("{\"error\":\"Parâmetros inválidos para a ferramenta.\"}");
        }
    }

    private Message send(MessageCreateParams params) {
        try {
            return client().messages().create(params);
        } catch (AnthropicException e) {
            // Não logar o corpo da requisição: contém dados financeiros do usuário.
            log.error("Erro na API da Anthropic: {}", e.getClass().getSimpleName() + ": " + e.getMessage());
            throw new AiProviderException("Falha ao comunicar com a IA", e);
        }
    }

    private static String extractText(Message response) {
        return response.content().stream()
            .flatMap(block -> block.text().stream())
            .map(textBlock -> textBlock.text())
            .collect(Collectors.joining("\n"))
            .trim();
    }

    private static Tool toAnthropicTool(AiToolDefinition definition) {
        Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
        definition.properties().forEach((name, schema) -> props.putAdditionalProperty(name, JsonValue.from(schema)));

        return Tool.builder()
            .name(definition.name())
            .description(definition.description())
            .inputSchema(Tool.InputSchema.builder()
                .properties(props.build())
                .required(definition.required())
                .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                .build())
            .build();
    }

    private AnthropicClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    if (!StringUtils.hasText(properties.getApiKey())) {
                        throw new AiProviderException("AI_API_KEY não configurada");
                    }
                    AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                        .apiKey(properties.getApiKey())
                        .timeout(properties.getTimeout())
                        .maxRetries(properties.getMaxRetries());
                    if (StringUtils.hasText(properties.getBaseUrl())) {
                        builder.baseUrl(properties.getBaseUrl());
                    }
                    client = builder.build();
                }
            }
        }
        return client;
    }
}
