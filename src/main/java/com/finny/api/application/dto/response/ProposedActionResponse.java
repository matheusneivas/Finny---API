package com.finny.api.application.dto.response;

import com.finny.api.domain.enums.ProposedActionStatus;
import com.finny.api.domain.enums.ProposedActionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Ação sugerida pela IA. Só é aplicada quando o usuário confirma no app.")
public class ProposedActionResponse {

    @Schema(description = "Identificador da proposta (para o app controlar o cartão)")
    private String id;

    private ProposedActionType type;

    @Schema(description = "Resumo em português, montado pelo servidor, para exibir no cartão",
            example = "Adicionar despesa de R$ 50,00 em Alimentação (Mercado) em 26/11/2026")
    private String summary;

    @Schema(description = "Estado do cartão: PENDING, CONFIRMED ou DISCARDED")
    @Builder.Default
    private ProposedActionStatus status = ProposedActionStatus.PENDING;

    @Schema(description = "Corpo pronto para o endpoint REST correspondente (já validado pelo servidor)")
    private Map<String, Object> payload;
}
