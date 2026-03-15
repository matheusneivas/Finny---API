package com.finny.api.application.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingStep4Request {

    @NotNull(message = "Informe se já investe")
    private Boolean hasInvestments;

    private Set<String> investmentTypes;

    @NotNull(message = "Informe quanto quer investir por mês")
    @Positive(message = "Meta de investimento mensal deve ser positiva")
    private BigDecimal monthlyInvestmentGoal;
}
