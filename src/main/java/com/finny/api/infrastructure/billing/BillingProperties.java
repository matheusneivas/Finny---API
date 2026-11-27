package com.finny.api.infrastructure.billing;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.billing")
public class BillingProperties {

    /** mercadopago | none (none = pagamentos desligados; ninguém consegue assinar). */
    private String provider = "none";

    /** Preço do Premium (plano único, mensal). */
    private BigDecimal monthlyPrice = new BigDecimal("9.90");

    /** Dias que um Pix pago libera de Premium. */
    private int pixDays = 30;

    /** Dias de teste grátis (uma vez por pessoa). 0 desliga. */
    private int trialDays = 7;

    /** Tolerância depois da data da cobrança no cartão (o gateway tenta de novo por alguns dias). */
    private int graceDays = 3;

    /** Para onde o Mercado Pago manda a pessoa depois do checkout (página do site). */
    private String returnUrl = "";

    private final MercadoPago mercadoPago = new MercadoPago();

    @Getter
    @Setter
    public static class MercadoPago {
        /** Access token (APP_USR-... em produção, TEST-... em testes). */
        private String accessToken = "";
        /** Assinatura secreta dos webhooks (Suas integrações > Webhooks). */
        private String webhookSecret = "";
        private String baseUrl = "https://api.mercadopago.com";
    }
}
