package com.finny.api.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Excluir a conta pede a senha de novo: um token roubado sozinho não apaga a conta. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteAccountRequest {

    @NotBlank(message = "Digite sua senha para confirmar")
    @Size(max = 72, message = "Senha inválida")
    private String password;
}
