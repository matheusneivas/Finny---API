package com.finny.api.presentation.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finny.api.application.service.BillingService;
import com.finny.api.infrastructure.billing.PaymentGateway;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook do Mercado Pago (público). Confere a assinatura x-signature e depois consulta o recurso
 * no Mercado Pago: mesmo um aviso falso não libera Premium sem um pagamento real.
 */
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
@Hidden
public class PaymentWebhookController {

    private final BillingService billingService;
    private final PaymentGateway gateway;
    private final ObjectMapper objectMapper;

    @PostMapping("/mercadopago")
    public ResponseEntity<Void> mercadoPago(
            @RequestHeader(value = "x-signature", required = false) String signature,
            @RequestHeader(value = "x-request-id", required = false) String requestId,
            @RequestParam(value = "data.id", required = false) String queryDataId,
            @RequestParam(value = "type", required = false) String queryType,
            @RequestBody(required = false) String rawBody) {
        JsonNode body = parse(rawBody);
        String type = body.path("type").asText(queryType == null ? "" : queryType);
        String dataId = queryDataId != null ? queryDataId : body.path("data").path("id").asText("");

        if (!gateway.verifyWebhook(signature, requestId, dataId)) {
            log.warn("Webhook do Mercado Pago com assinatura inválida | tipo: {}", type);
            return ResponseEntity.status(401).build();
        }
        billingService.handleNotification(type, dataId);
        return ResponseEntity.ok().build();
    }

    private JsonNode parse(String raw) {
        try {
            return raw == null || raw.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }
}
