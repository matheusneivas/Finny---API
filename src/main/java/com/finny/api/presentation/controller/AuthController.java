package com.finny.api.presentation.controller;

import com.finny.api.application.dto.request.LoginRequest;
import com.finny.api.application.dto.request.LogoutRequest;
import com.finny.api.application.dto.request.RefreshTokenRequest;
import com.finny.api.application.dto.request.RegisterRequest;
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
}
