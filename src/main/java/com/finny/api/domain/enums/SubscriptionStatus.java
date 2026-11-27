package com.finny.api.domain.enums;

/**
 * Estado da assinatura no cartão (cobrança automática todo mês).
 * <ul>
 *   <li>PENDING: criada, a pessoa ainda não terminou de cadastrar o cartão no Mercado Pago.</li>
 *   <li>ACTIVE: cartão autorizado; o gateway cobra todo mês.</li>
 *   <li>CANCELED: cancelada (pela pessoa ou pelo gateway). O Premium já pago continua até vencer.</li>
 * </ul>
 */
public enum SubscriptionStatus {
    PENDING,
    ACTIVE,
    CANCELED
}
