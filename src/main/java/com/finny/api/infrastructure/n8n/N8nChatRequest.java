package com.finny.api.infrastructure.n8n;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class N8nChatRequest {
    private String userId;
    private String message;
    private String userContext; // Dados do perfil do usuário para contexto
}
