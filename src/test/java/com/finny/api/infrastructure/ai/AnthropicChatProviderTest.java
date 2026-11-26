package com.finny.api.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Testa o loop de ferramentas do provedor Claude contra um servidor HTTP local (sem rede). */
class AnthropicChatProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentLinkedQueue<String> responses = new ConcurrentLinkedQueue<>();
    private final List<JsonNode> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private int status = 200;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            requests.add(mapper.readTree(exchange.getRequestBody()));
            byte[] body = (status == 200 ? responses.poll() : "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"x\"}}")
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private AnthropicChatProvider provider() {
        AiProperties props = new AiProperties();
        props.setApiKey("test-key");
        props.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        props.setMaxRetries(0);
        props.setTimeout(Duration.ofSeconds(10));
        return new AnthropicChatProvider(props);
    }

    private static AiChatRequest request() {
        return new AiChatRequest("instruções", "<dados_do_usuario></dados_do_usuario>",
            List.of(AiMessage.user("quanto guardei?")),
            List.of(new AiToolDefinition("list_goals", "lista metas", Map.of(), List.of())));
    }

    @Test
    void runsToolLoopAndReturnsFinalText() throws Exception {
        responses.add("""
            {"id":"msg_1","type":"message","role":"assistant","model":"claude-haiku-5-5",
             "content":[{"type":"tool_use","id":"toolu_1","name":"list_goals","input":{"x":1}}],
             "stop_reason":"tool_use","stop_sequence":null,"usage":{"input_tokens":10,"output_tokens":5}}""");
        responses.add("""
            {"id":"msg_2","type":"message","role":"assistant","model":"claude-haiku-5-5",
             "content":[{"type":"text","text":"Você guardou R$ 100,00."}],
             "stop_reason":"end_turn","stop_sequence":null,"usage":{"input_tokens":20,"output_tokens":7}}""");

        List<String> calledTools = new ArrayList<>();
        AiChatResult result = provider().chat(request(), (name, input) -> {
            calledTools.add(name + ":" + input.get("x"));
            return AiToolResult.ok("{\"goals\":[]}");
        });

        assertThat(result.reply()).isEqualTo("Você guardou R$ 100,00.");
        assertThat(result.inputTokens()).isEqualTo(30);
        assertThat(result.outputTokens()).isEqualTo(12);
        assertThat(calledTools).containsExactly("list_goals:1");

        // Primeira chamada: modelo, sistema com cache nas instruções fixas, ferramentas
        JsonNode first = requests.get(0);
        assertThat(first.path("model").asText()).isEqualTo("claude-haiku-5-5");
        assertThat(first.path("system").get(0).path("cache_control").path("type").asText()).isEqualTo("ephemeral");
        assertThat(first.path("tools").get(0).path("name").asText()).isEqualTo("list_goals");
        assertThat(first.path("output_config").path("effort").asText()).isEqualTo("low");
        assertThat(first.has("temperature")).isFalse();

        // Segunda chamada: devolve o tool_use e o tool_result correspondente
        JsonNode second = requests.get(1);
        JsonNode messages = second.path("messages");
        assertThat(messages.get(1).path("role").asText()).isEqualTo("assistant");
        JsonNode toolResult = messages.get(2).path("content").get(0);
        assertThat(toolResult.path("type").asText()).isEqualTo("tool_result");
        assertThat(toolResult.path("tool_use_id").asText()).isEqualTo("toolu_1");
    }

    @Test
    void refusalIsReported() {
        responses.add("""
            {"id":"msg_1","type":"message","role":"assistant","model":"claude-haiku-5-5",
             "content":[],"stop_reason":"refusal","stop_sequence":null,
             "usage":{"input_tokens":3,"output_tokens":0}}""");
        AiChatResult result = provider().chat(request(), (n, i) -> AiToolResult.ok("{}"));
        assertThat(result.refused()).isTrue();
    }

    @Test
    void apiErrorBecomesProviderException() {
        status = 401;
        assertThatThrownBy(() -> provider().chat(request(), (n, i) -> AiToolResult.ok("{}")))
            .isInstanceOf(AiProviderException.class);
    }

    @Test
    void missingApiKeyFailsClearly() {
        AiProperties props = new AiProperties();
        assertThatThrownBy(() -> new AnthropicChatProvider(props).chat(request(), (n, i) -> AiToolResult.ok("{}")))
            .isInstanceOf(AiProviderException.class)
            .hasMessageContaining("AI_API_KEY");
    }
}
