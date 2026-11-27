package com.finny.api.application.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @NotBlank(message = "E-mail é obrigatório")
    @Email(message = "E-mail inválido")
    @Size(max = 255, message = "E-mail deve ter no máximo 255 caracteres")
    String email,

    @NotBlank(message = "Informe o código")
    @Pattern(regexp = "\\d{6}", message = "O código tem 6 números")
    String code,

    // Mesma regra do cadastro
    @NotBlank(message = "Senha é obrigatória")
    @Size(min = 8, max = 72, message = "Senha deve ter entre 8 e 72 caracteres")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
             message = "Senha deve conter letras maiúsculas, minúsculas e números")
    String newPassword) {
}
