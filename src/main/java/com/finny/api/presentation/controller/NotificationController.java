package com.finny.api.presentation.controller;

import com.finny.api.application.dto.request.DeviceTokenRequest;
import com.finny.api.application.dto.response.ApiResponse;
import com.finny.api.domain.entity.User;
import com.finny.api.domain.entity.UserDeviceToken;
import com.finny.api.domain.repository.UserDeviceTokenRepository;
import com.finny.api.domain.repository.UserRepository;
import com.finny.api.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Gerenciamento de tokens de push notification")
public class NotificationController {

    private final UserDeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;

    @PostMapping("/token")
    @Operation(summary = "Registrar token de dispositivo para push notification")
    public ResponseEntity<ApiResponse<Void>> registerToken(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody DeviceTokenRequest request) {

        UUID userId = extractUserId(authHeader);

        if (!deviceTokenRepository.existsByToken(request.getToken())) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

            UserDeviceToken deviceToken = new UserDeviceToken();
            deviceToken.setUser(user);
            deviceToken.setToken(request.getToken());
            deviceToken.setPlatform(request.getPlatform() != null ? request.getPlatform() : "WEB");
            deviceTokenRepository.save(deviceToken);
        }

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Token registrado com sucesso")
                .build());
    }

    @DeleteMapping("/token")
    @Operation(summary = "Remover token de dispositivo")
    public ResponseEntity<ApiResponse<Void>> removeToken(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam String token) {

        deviceTokenRepository.deleteByToken(token);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Token removido com sucesso")
                .build());
    }

    private UUID extractUserId(String authHeader) {
        String jwt = authHeader.replace("Bearer ", "");
        return tokenProvider.getUserIdFromToken(jwt);
    }
}
