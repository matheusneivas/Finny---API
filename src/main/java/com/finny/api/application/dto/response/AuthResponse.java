package com.finny.api.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    /** Uso único: a cada /auth/refresh o app deve guardar o novo valor. */
    private String refreshToken;
    @Builder.Default
    private String tokenType = "Bearer";
    /** Validade do access token, em segundos. */
    private Long expiresIn;
    private UserResponse user;
}
