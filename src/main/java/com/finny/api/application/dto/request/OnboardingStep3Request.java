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
public class OnboardingStep3Request {

    @NotNull(message = "Informe se possui dívidas")
    private Boolean hasDebts;

    @Positive(message = "Total de dívidas deve ser positivo")
    private BigDecimal totalDebt;

    private Set<String> debtTypes;

    @NotNull(message = "Informe se possui reserva de emergência")
    private Boolean hasEmergencyFund;

    @Positive(message = "Meses de reserva deve ser positivo")
    private Integer emergencyFundMonths;
}
