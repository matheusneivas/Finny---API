package com.finny.api.application.dto.request;

import com.finny.api.domain.enums.ProposedActionStatus;
import jakarta.validation.constraints.NotNull;

/** Novo estado do cartão: CONFIRMED ou DISCARDED. */
public record UpdateActionStatusRequest(@NotNull(message = "Informe o status") ProposedActionStatus status) {
}
