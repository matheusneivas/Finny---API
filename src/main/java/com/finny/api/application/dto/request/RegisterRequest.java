package com.finny.api.application.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "E-mail é obrigatório")
    @Email(message = "E-mail inválido")
    @Size(max = 255, message = "E-mail deve ter no máximo 255 caracteres")
    private String email;

    @NotBlank(message = "Senha é obrigatória")
    // BCrypt considera só os primeiros 72 bytes: limitar evita senhas truncadas silenciosamente
    @Size(min = 8, max = 72, message = "Senha deve ter entre 8 e 72 caracteres")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
             message = "Senha deve conter letras maiúsculas, minúsculas e números")
    private String password;

    @NotBlank(message = "Nome completo é obrigatório")
    @Size(max = 120, message = "Nome deve ter no máximo 120 caracteres")
    private String fullName;
}
