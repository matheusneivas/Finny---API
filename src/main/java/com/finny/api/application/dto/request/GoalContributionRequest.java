package com.finny.api.application.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoalContributionRequest {

    @NotNull(message = "Valor da contribuição é obrigatório")
    @Positive(message = "Valor deve ser maior que zero")
    @Digits(integer = 13, fraction = 2, message = "Formato de valor inválido")
    private BigDecimal amount;
}
