package com.finny.api.application.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogoutRequest {

    /** Refresh token da sessão a encerrar. */
    @Size(max = 2048)
    private String refreshToken;
}
