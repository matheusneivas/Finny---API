package com.finny.api.infrastructure.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finny.api.application.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mercado Pago (www.mercadopago.com.br/developers).
 * <ul>
 *   <li>Cartão: assinatura sem plano ({@code /preapproval}); a pessoa cadastra o cartão no
 *       {@code init_point} e o Mercado Pago cobra todo mês ({@code /authorized_payments}).</li>
 *   <li>Pix: pagamento avulso ({@code /v1/payments} com payment_method_id=pix).</li>
 * </ul>
 * O webhook só traz o id do recurso; a API sempre consulta o Mercado Pago para saber o estado real.
 */
@Slf4j
class MercadoPagoGateway implements PaymentGateway {

    private static final ZoneId BRT = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter MP_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final String UNAVAILABLE = "Pagamento indisponível no momento. Tente de novo em instantes.";

    private final BillingProperties.MercadoPago config;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    MercadoPagoGateway(BillingProperties.MercadoPago config, ObjectMapper objectMapper, Clock clock) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override public String name() { return "mercadopago"; }

    @Override public boolean enabled() { return true; }

    @Override
    public CardCheckout createCardSubscription(String payerEmail, BigDecimal monthlyValue, String reason,
                                               String externalReference, String returnUrl) {
        Map<String, Object> recurring = new LinkedHashMap<>();
        recurring.put("frequency", 1);
        recurring.put("frequency_type", "months");
        recurring.put("transaction_amount", monthlyValue);
        recurring.put("currency_id", "BRL");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", reason);
        body.put("external_reference", externalReference);
        body.put("payer_email", payerEmail);
        body.put("auto_recurring", recurring);
        body.put("back_url", returnUrl == null || returnUrl.isBlank() ? "https://www.mercadopago.com.br" : returnUrl);
        body.put("status", "pending");

        JsonNode created = call("POST", "/preapproval", body, null);
        return new CardCheckout(created.path("id").asText(), created.path("init_point").asText(null));
    }

    @Override
    public void cancelCardSubscription(String subscriptionId) {
        call("PUT", "/preapproval/" + subscriptionId, Map.of("status", "cancelled"), null);
    }

    @Override
    public CardSubscription getCardSubscription(String subscriptionId) {
        JsonNode s = call("GET", "/preapproval/" + subscriptionId, null, null);
        String status = switch (s.path("status").asText("")) {
            case "authorized" -> "ACTIVE";
            case "pending" -> "PENDING";
            default -> "CANCELED"; // cancelled, paused
        };
        return new CardSubscription(s.path("id").asText(), status, s.path("external_reference").asText(""));
    }

    @Override
    public CardCharge getCardCharge(String chargeId) {
        JsonNode c = call("GET", "/authorized_payments/" + chargeId, null, null);
        boolean paid = "processed".equals(c.path("status").asText())
            && "approved".equals(c.path("payment").path("status").asText());
        return new CardCharge(c.path("id").asText(chargeId), c.path("preapproval_id").asText(""), paid,
            parseDate(c.path("debit_date").asText(c.path("date_created").asText(""))));
    }

    @Override
    public PixCharge createPix(String payerEmail, BigDecimal value, String description, String externalReference,
                               String idempotencyKey) {
        OffsetDateTime expires = OffsetDateTime.now(clock.withZone(BRT)).plusHours(24);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transaction_amount", value);
        body.put("description", description);
        body.put("payment_method_id", "pix");
        body.put("payer", Map.of("email", payerEmail));
        body.put("external_reference", externalReference);
        body.put("date_of_expiration", expires.format(MP_DATE));

        JsonNode p = call("POST", "/v1/payments", body, idempotencyKey);
        JsonNode data = p.path("point_of_interaction").path("transaction_data");
        return new PixCharge(p.path("id").asText(), data.path("qr_code").asText(null),
            data.path("qr_code_base64").asText(null), data.path("ticket_url").asText(null), expires);
    }

    @Override
    public Payment getPayment(String paymentId) {
        JsonNode p = call("GET", "/v1/payments/" + paymentId, null, null);
        String status = switch (p.path("status").asText("")) {
            case "approved" -> "APPROVED";
            case "pending", "in_process", "authorized" -> "PENDING";
            case "refunded", "charged_back" -> "REVERSED";
            default -> "OTHER";
        };
        return new Payment(p.path("id").asText(paymentId), status, p.path("external_reference").asText(""));
    }

    /**
     * Header x-signature = "ts=...,v1=...". O v1 é o HMAC-SHA256 (chave = assinatura secreta) de
     * "id:&lt;data.id&gt;;request-id:&lt;x-request-id&gt;;ts:&lt;ts&gt;;".
     */
    @Override
    public boolean verifyWebhook(String signatureHeader, String requestId, String dataId) {
        if (signatureHeader == null || signatureHeader.isBlank()) return false;
        String ts = null;
        String v1 = null;
        for (String part : signatureHeader.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            if (kv[0].equals("ts")) ts = kv[1].trim();
            if (kv[0].equals("v1")) v1 = kv[1].trim();
        }
        if (ts == null || v1 == null) return false;

        StringBuilder manifest = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            manifest.append("id:").append(dataId.matches("[A-Za-z0-9]+") ? dataId.toLowerCase() : dataId).append(';');
        }
        if (requestId != null && !requestId.isBlank()) manifest.append("request-id:").append(requestId).append(';');
        manifest.append("ts:").append(ts).append(';');
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(config.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(manifest.toString().getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                v1.toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private JsonNode call(String method, String path, Object body, String idempotencyKey) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(stripSlash(config.getBaseUrl()) + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + config.getAccessToken())
                .header("Accept", "application/json");
            if (idempotencyKey != null) request.header("X-Idempotency-Key", idempotencyKey);
            if (body != null) {
                request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            } else {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                String detail = response.body() == null ? "" : response.body();
                log.error("Mercado Pago recusou {} {} | status: {} | resposta: {}", method, path, response.statusCode(),
                    detail.length() > 400 ? detail.substring(0, 400) : detail);
                throw new ServiceUnavailableException(UNAVAILABLE);
            }
            String text = response.body();
            return objectMapper.readTree(text == null || text.isBlank() ? "{}" : text);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException(UNAVAILABLE);
        } catch (IOException e) {
            log.error("Falha de rede ao chamar o Mercado Pago: {}", e.getMessage());
            throw new ServiceUnavailableException(UNAVAILABLE);
        }
    }

    private LocalDate parseDate(String value) {
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(BRT).toLocalDate();
        } catch (DateTimeParseException e) {
            return LocalDate.now(clock.withZone(BRT));
        }
    }

    private static String stripSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
