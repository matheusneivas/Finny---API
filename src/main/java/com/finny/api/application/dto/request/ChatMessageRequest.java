package com.finny.api.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageRequest {

    @NotBlank(message = "Mensagem é obrigatória")
    @Size(max = 2000, message = "Mensagem deve ter no máximo 2000 caracteres")
    private String message;
}
