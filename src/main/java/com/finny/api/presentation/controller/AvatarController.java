package com.finny.api.presentation.controller;

import com.finny.api.application.dto.response.AvatarResponse;
import com.finny.api.application.service.AvatarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/avatar")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "Endpoints de perfil do usuário")
@SecurityRequirement(name = "bearerAuth")
public class AvatarController {

    private final AvatarService avatarService;

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Enviar ou trocar a foto de perfil",
        description = "Campo \"file\": JPEG, PNG ou WEBP de até 2 MB. O servidor re-encoda para JPEG até 512x512.")
    public ResponseEntity<AvatarResponse> upload(@AuthenticationPrincipal UserDetails userDetails,
                                                 @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(avatarService.upload(userId(userDetails), file));
    }

    @GetMapping
    @Operation(summary = "Baixar a foto de perfil (404 se não houver)")
    public ResponseEntity<byte[]> get(@AuthenticationPrincipal UserDetails userDetails) {
        AvatarService.AvatarContent avatar = avatarService.get(userId(userDetails));
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(avatar.contentType()))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
            .eTag(avatar.version())
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Disposition", "inline")
            .header("Content-Security-Policy", "default-src 'none'")
            .body(avatar.content());
    }

    @DeleteMapping
    @Operation(summary = "Remover a foto de perfil")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserDetails userDetails) {
        avatarService.delete(userId(userDetails));
        return ResponseEntity.noContent().build();
    }

    private static UUID userId(UserDetails userDetails) {
        return UUID.fromString(userDetails.getUsername());
    }
}
