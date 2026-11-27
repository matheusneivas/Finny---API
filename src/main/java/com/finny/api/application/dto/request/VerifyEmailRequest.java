package com.finny.api.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyEmailRequest(
    @NotBlank(message = "Informe o código")
    @Pattern(regexp = "\\d{6}", message = "O código tem 6 números")
    String code) {
}
