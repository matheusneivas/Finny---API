package com.finny.api.presentation.controller;

import com.finny.api.application.dto.request.DeviceTokenRequest;
import com.finny.api.application.dto.response.ApiResponse;
import com.finny.api.domain.entity.UserDeviceToken;
import com.finny.api.domain.repository.UserDeviceTokenRepository;
import com.finny.api.domain.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Tokens de push notification do dispositivo")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final UserDeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;

    @PostMapping("/token")
    @Transactional
    @Operation(summary = "Registrar token de push do dispositivo",
        description = "Se o token já estava ligado a outra conta (troca de conta no mesmo aparelho), "
            + "ele passa para a conta atual.")
    public ResponseEntity<ApiResponse<Void>> registerToken(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody DeviceTokenRequest request) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        UserDeviceToken deviceToken = deviceTokenRepository.findByToken(request.getToken())
            .orElseGet(UserDeviceToken::new);
        deviceToken.setUser(userRepository.getReferenceById(userId));
        deviceToken.setToken(request.getToken());
        deviceToken.setPlatform(request.getPlatform() != null ? request.getPlatform() : "WEB");
        deviceTokenRepository.save(deviceToken);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Token registrado com sucesso")
                .build());
    }

    @DeleteMapping("/token")
    @Transactional
    @Operation(summary = "Remover token de push do dispositivo (somente da própria conta)")
    public ResponseEntity<ApiResponse<Void>> removeToken(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String token) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        deviceTokenRepository.deleteByTokenAndUserId(token, userId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Token removido com sucesso")
                .build());
    }
}
