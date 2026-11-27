package com.finny.api.infrastructure.billing;

import java.math.BigDecimal;

/** Usado com app.billing.provider=none. */
class DisabledPaymentGateway implements PaymentGateway {

    @Override public String name() { return "none"; }

    @Override public boolean enabled() { return false; }

    @Override public CardCheckout createCardSubscription(String payerEmail, BigDecimal monthlyValue, String reason,
                                                         String externalReference, String returnUrl) {
        throw off();
    }

    @Override public void cancelCardSubscription(String subscriptionId) { throw off(); }

    @Override public CardSubscription getCardSubscription(String subscriptionId) { throw off(); }

    @Override public CardCharge getCardCharge(String chargeId) { throw off(); }

    @Override public PixCharge createPix(String payerEmail, BigDecimal value, String description,
                                         String externalReference, String idempotencyKey) {
        throw off();
    }

    @Override public Payment getPayment(String paymentId) { throw off(); }

    @Override public boolean verifyWebhook(String signatureHeader, String requestId, String dataId) { return false; }

    private static IllegalStateException off() {
        return new IllegalStateException("Pagamentos desligados");
    }
}
