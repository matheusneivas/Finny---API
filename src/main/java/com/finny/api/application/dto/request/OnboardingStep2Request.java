package com.finny.api.application.dto.request;

import com.finny.api.domain.enums.IncomeRange;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingStep2Request {

    @NotNull(message = "Faixa de renda é obrigatória")
    private IncomeRange incomeRange;

    @NotNull(message = "Informe se paga aluguel")
    private Boolean paysRent;

    @Positive(message = "Valor do aluguel deve ser positivo")
    private BigDecimal monthlyRent;
}
