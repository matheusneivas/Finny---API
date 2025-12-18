package com.finny.api.infrastructure.n8n;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class N8nClient {

    @Value("${n8n.base-url}")
    private String baseUrl;

    @Value("${n8n.api-key}")
    private String apiKey;

    @Value("${n8n.webhooks.chat}")
    private String chatWebhook;

    @Value("${n8n.webhooks.ocr}")
    private String ocrWebhook;

    private final RestTemplate restTemplate = new RestTemplate();

    public N8nChatResponse sendChatMessage(N8nChatRequest request) {
        // TODO: Implementar chamada real ao n8n
        log.debug("Sending chat message to n8n (MOCKED)");

        return N8nChatResponse.builder()
                .reply("Resposta mockada do n8n")
                .tokensUsed(100)
                .workflowId("mock-workflow-id")
                .build();
    }

    public void triggerOcrProcessing(N8nOcrRequest request) {
        // TODO: Implementar chamada real ao n8n
        log.debug("Triggering OCR processing on n8n (MOCKED)");

        // Este método apenas dispara o processamento assíncrono
        // O resultado será enviado de volta via callback
    }
}
