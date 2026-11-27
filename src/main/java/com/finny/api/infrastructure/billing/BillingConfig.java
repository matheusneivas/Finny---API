package com.finny.api.infrastructure.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@Slf4j
public class BillingConfig {

    @Bean
    PaymentGateway paymentGateway(BillingProperties properties, ObjectMapper objectMapper, Clock clock) {
        String provider = properties.getProvider() == null ? "none" : properties.getProvider().trim().toLowerCase();
        switch (provider) {
            case "mercadopago" -> {
                BillingProperties.MercadoPago mp = properties.getMercadoPago();
                if (mp.getAccessToken() == null || mp.getAccessToken().isBlank()) {
                    throw new IllegalStateException("app.billing.provider=mercadopago exige MERCADOPAGO_ACCESS_TOKEN");
                }
                if (mp.getWebhookSecret() == null || mp.getWebhookSecret().isBlank()) {
                    throw new IllegalStateException("app.billing.provider=mercadopago exige MERCADOPAGO_WEBHOOK_SECRET");
                }
                log.info("Pagamentos via Mercado Pago ({})",
                    mp.getAccessToken().startsWith("TEST-") ? "modo de teste" : "produção");
                return new MercadoPagoGateway(mp, objectMapper, clock);
            }
            case "none" -> {
                log.warn("Pagamentos desligados (BILLING_PROVIDER=none): ninguém consegue assinar o Premium");
                return new DisabledPaymentGateway();
            }
            default -> throw new IllegalStateException("app.billing.provider desconhecido: " + provider);
        }
    }
}
