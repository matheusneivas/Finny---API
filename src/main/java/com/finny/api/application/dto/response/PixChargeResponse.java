package com.finny.api.application.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * @param qrCode       código "copia e cola"
 * @param qrCodeBase64 imagem PNG do QR code em base64 (use em data:image/png;base64,...)
 * @param ticketUrl    página do Mercado Pago com o QR code (alternativa)
 */
public record PixChargeResponse(
    String paymentId,
    BigDecimal amount,
    int premiumDays,
    String qrCode,
    String qrCodeBase64,
    String ticketUrl,
    OffsetDateTime expiresAt
) {}
