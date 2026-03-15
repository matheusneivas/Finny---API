package com.finny.api.presentation.controller;

import com.finny.api.application.dto.request.*;
import com.finny.api.application.dto.response.OnboardingStepResponse;
import com.finny.api.application.service.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
@Tag(name = "Onboarding", description = "Configuração inicial do perfil financeiro")
@SecurityRequirement(name = "bearerAuth")
public class OnboardingController {

    private final OnboardingService onboardingService;

    @GetMapping("/status")
    @Operation(summary = "Verificar progresso do onboarding",
               description = "Retorna o passo atual, percentual de progresso e próxima etapa")
    public ResponseEntity<OnboardingStepResponse> getStatus(
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(onboardingService.getStatus(userId));
    }

    @PostMapping("/step/1")
    @Operation(summary = "Passo 1 — Informações básicas",
               description = "Data de nascimento e ocupação")
    public ResponseEntity<OnboardingStepResponse> step1(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody OnboardingStep1Request request) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(onboardingService.saveStep1(userId, request));
    }

    @PostMapping("/step/2")
    @Operation(summary = "Passo 2 — Renda e moradia",
               description = "Faixa de renda mensal e se paga aluguel (e quanto)")
    public ResponseEntity<OnboardingStepResponse> step2(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody OnboardingStep2Request request) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(onboardingService.saveStep2(userId, request));
    }

    @PostMapping("/step/3")
    @Operation(summary = "Passo 3 — Dívidas e reserva de emergência",
               description = "Possui dívidas? Qual o total? Tem reserva de emergência?")
    public ResponseEntity<OnboardingStepResponse> step3(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody OnboardingStep3Request request) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(onboardingService.saveStep3(userId, request));
    }

    @PostMapping("/step/4")
    @Operation(summary = "Passo 4 — Investimentos",
               description = "Já investe? Em quê? Quanto quer investir por mês?")
    public ResponseEntity<OnboardingStepResponse> step4(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody OnboardingStep4Request request) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(onboardingService.saveStep4(userId, request));
    }

    @PostMapping("/step/5")
    @Operation(summary = "Passo 5 — Objetivos e preferências",
               description = "Metas financeiras, preferências de controle e estilo de comunicação")
    public ResponseEntity<OnboardingStepResponse> step5(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody OnboardingStep5Request request) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(onboardingService.saveStep5(userId, request));
    }
}
