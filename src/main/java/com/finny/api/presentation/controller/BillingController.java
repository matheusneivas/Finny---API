package com.finny.api.presentation.controller;

import com.finny.api.application.dto.response.BillingPlansResponse;
import com.finny.api.application.dto.response.PixChargeResponse;
import com.finny.api.application.dto.response.SubscriptionResponse;
import com.finny.api.application.service.BillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
@Tag(name = "Assinatura", description = "Plano Premium: teste grátis, cartão (todo mês) e Pix (30 dias)")
@SecurityRequirement(name = "bearerAuth")
public class BillingController {

    private final BillingService billingService;

    @GetMapping("/plans")
    @Operation(summary = "Preço do Premium e limites do plano grátis")
    public ResponseEntity<BillingPlansResponse> plans() {
        return ResponseEntity.ok(billingService.plans());
    }

    @GetMapping("/subscription")
    @Operation(summary = "Plano atual, até quando vale o Premium e uso do mês")
    public ResponseEntity<SubscriptionResponse> status(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(billingService.status(userId(user)));
    }

    @PostMapping("/trial")
    @Operation(summary = "Começar o teste grátis (uma vez por pessoa, sem cartão). 409 se já usou.")
    public ResponseEntity<SubscriptionResponse> trial(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(billingService.startTrial(userId(user)));
    }

    @PostMapping("/card")
    @Operation(summary = "Assinar no cartão",
        description = "Devolve card.checkoutUrl: abra essa página do Mercado Pago para a pessoa cadastrar o cartão. "
            + "O Premium é liberado quando o Mercado Pago confirmar a cobrança (consulte /billing/subscription).")
    public ResponseEntity<SubscriptionResponse> card(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(billingService.subscribeWithCard(userId(user)));
    }

    @PostMapping("/pix")
    @Operation(summary = "Gerar Pix de 30 dias de Premium",
        description = "Mostre o QR code (qrCodeBase64) e o copia e cola (qrCode). O Pix vale 24 h. Mande "
            + "Idempotency-Key para não gerar outro Pix se o pedido for repetido.")
    public ResponseEntity<PixChargeResponse> pix(@AuthenticationPrincipal UserDetails user,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseEntity.ok(billingService.payWithPix(userId(user), key));
    }

    @PostMapping("/card/cancel")
    @Operation(summary = "Cancelar a cobrança no cartão (o Premium já pago continua até vencer)")
    public ResponseEntity<SubscriptionResponse> cancel(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(billingService.cancelCard(userId(user)));
    }

    private static UUID userId(UserDetails user) {
        return UUID.fromString(user.getUsername());
    }
}
