package com.finny.api.application.dto.response;

import com.finny.api.domain.enums.UserPlan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private UUID id;
    private String email;
    private String phone;
    private String name;
    private Boolean onboardingCompleted;
    private UserPlan plan;
    private LocalDateTime planExpiresAt;
    private Boolean emailVerified;
    /** Caminho relativo da foto (ex.: /api/v1/users/me/avatar?v=abc), ou null. */
    private String avatarUrl;
    private LocalDateTime createdAt;
}
