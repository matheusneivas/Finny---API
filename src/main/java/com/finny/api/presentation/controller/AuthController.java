package com.finny.api.presentation.controller;

import com.finny.api.application.dto.request.LoginRequest;
import com.finny.api.application.dto.request.LogoutRequest;
import com.finny.api.application.dto.request.RefreshTokenRequest;
import com.finny.api.application.dto.request.ForgotPasswordRequest;
import com.finny.api.application.dto.request.RegisterRequest;
import com.finny.api.application.dto.request.ResetPasswordRequest;
import com.finny.api.application.dto.request.VerifyEmailRequest;
import com.finny.api.application.dto.response.UserResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.UUID;
import com.finny.api.application.dto.response.ApiResponse;
import com.finny.api.application.dto.response.AuthResponse;
import com.finny.api.application.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints de autenticação")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Registrar novo usuário")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Fazer login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar access token usando refresh token",
        description = "Rotação: devolve um NOVO refresh token e invalida o anterior. Reusar um refresh token "
            + "já trocado encerra a sessão (possível roubo de token).")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Fazer logout", description = "Revoga a sessão do refresh token informado. "
        + "O access token atual expira sozinho em poucos minutos; o app deve descartá-lo.")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody(required = false) LogoutRequest request) {
        authService.logout(request != null ? request.getRefreshToken() : null);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Logout realizado com sucesso")
                .build());
    }

    @PostMapping("/email/verify")
    @Operation(summary = "Confirmar o e-mail com o código de 6 dígitos",
        description = "Exige Bearer. Código vale 15 minutos e aceita 5 tentativas. Devolve o usuário atualizado.")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UserResponse> verifyEmail(@AuthenticationPrincipal UserDetails userDetails,
                                                    @Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok(authService.verifyEmail(UUID.fromString(userDetails.getUsername()), request.code()));
    }

    @PostMapping("/email/resend")
    @Operation(summary = "Reenviar o código de verificação",
        description = "Exige Bearer. Intervalo mínimo de 60 s (429 com Retry-After).")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> resendVerification(@AuthenticationPrincipal UserDetails userDetails) {
        authService.resendVerification(UUID.fromString(userDetails.getUsername()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/forgot")
    @Operation(summary = "Pedir código para criar nova senha",
        description = "Sempre 204, exista ou não a conta. Intervalo mínimo de 60 s por e-mail (429 com Retry-After).")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Criar nova senha com o código recebido",
        description = "Encerra todas as sessões abertas. Depois, faça login com a senha nova.")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.email(), request.code(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
