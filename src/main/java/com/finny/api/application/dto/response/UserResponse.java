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
    private String fullName;
    private UserPlan plan;
    private LocalDateTime planExpiresAt;
    private Boolean emailVerified;
    private LocalDateTime createdAt;
}
