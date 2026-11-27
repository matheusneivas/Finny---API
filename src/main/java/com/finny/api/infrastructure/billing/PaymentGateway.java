package com.finny.api.infrastructure.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Gateway de pagamento (Mercado Pago hoje; dá para trocar sem mexer no resto da API). */
public interface PaymentGateway {

    /** Nome gravado na assinatura (ex.: "mercadopago"). */
    String name();

    /** false quando nenhum gateway está configurado: as rotas de pagamento respondem 503. */
    boolean enabled();

    /** Cria a assinatura mensal no cartão. Devolve o id e o link onde a pessoa cadastra o cartão. */
    CardCheckout createCardSubscription(String payerEmail, BigDecimal monthlyValue, String reason,
                                        String externalReference, String returnUrl);

    /** Para de cobrar o cartão. */
    void cancelCardSubscription(String subscriptionId);

    /** Lê o estado atual da assinatura no gateway. */
    CardSubscription getCardSubscription(String subscriptionId);

    /** Lê uma cobrança mensal da assinatura no cartão. */
    CardCharge getCardCharge(String chargeId);

    /** Cria uma cobrança Pix avulsa. */
    PixCharge createPix(String payerEmail, BigDecimal value, String description, String externalReference,
                        String idempotencyKey);

    /** Lê um pagamento avulso (Pix). */
    Payment getPayment(String paymentId);

    /** Confere a assinatura do webhook. */
    boolean verifyWebhook(String signatureHeader, String requestId, String dataId);

    record CardCheckout(String id, String checkoutUrl) {}

    /** status: PENDING, ACTIVE ou CANCELED. */
    record CardSubscription(String id, String status, String externalReference) {}

    /** paid = a cobrança do mês entrou. */
    record CardCharge(String id, String subscriptionId, boolean paid, LocalDate chargeDate) {}

    record PixCharge(String id, String qrCode, String qrCodeBase64, String ticketUrl, OffsetDateTime expiresAt) {}

    /** status: APPROVED, PENDING, REVERSED (estornado/contestado) ou OTHER. */
    record Payment(String id, String status, String externalReference) {}
}
