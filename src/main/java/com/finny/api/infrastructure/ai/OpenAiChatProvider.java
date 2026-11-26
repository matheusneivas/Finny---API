package com.finny.api.infrastructure.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provedor alternativo (OpenAI Chat Completions). Ative com AI_PROVIDER=openai e AI_MODEL=&lt;modelo&gt;.
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
@Slf4j
public class OpenAiChatProvider implements AiChatProvider {

    private static final String DEFAULT_URL = "https://api.openai.com/v1";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenAiChatProvider(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.getTimeout().toMillis());
        factory.setReadTimeout((int) properties.getTimeout().toMillis());
        this.restClient = RestClient.builder()
            .baseUrl(StringUtils.hasText(properties.getBaseUrl()) ? properties.getBaseUrl() : DEFAULT_URL)
            .requestFactory(factory)
            .build();
        log.info("Provedor de IA: OpenAI | modelo: {}", properties.getModel());
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public AiChatResult chat(AiChatRequest request, AiToolHandler toolHandler) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new AiProviderException("AI_API_KEY não configurada");
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system",
            "content", request.staticInstructions() + "\n\n" + request.userContext()));
        for (AiMessage message : request.history()) {
            messages.add(Map.of(
                "role", message.role() == AiMessage.Role.USER ? "user" : "assistant",
                "content", message.content()));
        }

        List<Map<String, Object>> tools = request.tools().stream().map(this::toOpenAiTool).toList();
        long inputTokens = 0;
        long outputTokens = 0;

        for (int iteration = 0; iteration < properties.getMaxToolIterations(); iteration++) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", properties.getModel());
            body.put("messages", messages);
            body.put("max_completion_tokens", properties.getMaxTokens());
            if (!tools.isEmpty()) {
                body.put("tools", tools);
            }

            JsonNode response = send(body);
            JsonNode usage = response.path("usage");
            inputTokens += usage.path("prompt_tokens").asLong(0);
            outputTokens += usage.path("completion_tokens").asLong(0);

            JsonNode choice = response.path("choices").path(0);
            JsonNode message = choice.path("message");
            JsonNode toolCalls = message.path("tool_calls");

            if (!toolCalls.isArray() || toolCalls.isEmpty()) {
                if (message.hasNonNull("refusal")) {
                    return new AiChatResult(null, inputTokens, outputTokens, true);
                }
                return new AiChatResult(message.path("content").asText("").trim(), inputTokens, outputTokens, false);
            }

            messages.add(objectMapper.convertValue(message, MAP_TYPE));
            for (JsonNode call : toolCalls) {
                String name = call.path("function").path("name").asText();
                AiToolResult result;
                try {
                    Map<String, Object> input = objectMapper.readValue(
                        call.path("function").path("arguments").asText("{}"), MAP_TYPE);
                    result = toolHandler.handle(name, input);
                } catch (Exception e) {
                    result = AiToolResult.error("{\"error\":\"Parâmetros inválidos para a ferramenta.\"}");
                }
                messages.add(Map.of(
                    "role", "tool",
                    "tool_call_id", call.path("id").asText(),
                    "content", result.content()));
            }
        }

        return new AiChatResult(
            "Não consegui concluir esse pedido agora. Pode tentar dividir em partes menores?",
            inputTokens, outputTokens, false);
    }

    private JsonNode send(Map<String, Object> body) {
        try {
            return restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + properties.getApiKey())
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        } catch (RestClientException e) {
            log.error("Erro na API da OpenAI: {}", e.getClass().getSimpleName());
            throw new AiProviderException("Falha ao comunicar com a IA", e);
        }
    }

    private Map<String, Object> toOpenAiTool(AiToolDefinition definition) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", definition.properties());
        parameters.put("required", definition.required());
        parameters.put("additionalProperties", false);
        return Map.of(
            "type", "function",
            "function", Map.of(
                "name", definition.name(),
                "description", definition.description(),
                "parameters", parameters));
    }
}
