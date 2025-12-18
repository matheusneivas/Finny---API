package com.finny.api.application.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoalRequest {

    @NotBlank(message = "Nome da meta é obrigatório")
    @Size(max = 50, message = "Nome deve ter no máximo 50 caracteres")
    private String name;

    @NotNull(message = "Valor alvo é obrigatório")
    @Positive(message = "Valor alvo deve ser maior que zero")
    @Digits(integer = 13, fraction = 2, message = "Formato de valor inválido")
    private BigDecimal targetAmount;

    @NotNull(message = "Data limite é obrigatória")
    @Future(message = "Data limite deve ser no futuro")
    private LocalDate deadline;

    private UUID relatedCategoryId;

    @Size(max = 50, message = "Ícone deve ter no máximo 50 caracteres")
    private String icon;
}
