package com.finny.api.application.dto.response;

/** Caminho relativo da foto recém-enviada (ex.: /api/v1/users/me/avatar?v=abc). */
public record AvatarResponse(String avatarUrl) {
}
