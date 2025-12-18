package com.finny.api.application.dto.request;

import com.finny.api.domain.enums.TransactionType;
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
public class TransactionRequest {

    @NotNull(message = "Valor é obrigatório")
    @Positive(message = "Valor deve ser maior que zero")
    @Digits(integer = 13, fraction = 2, message = "Formato de valor inválido")
    private BigDecimal amount;

    @NotNull(message = "Tipo é obrigatório")
    private TransactionType type;

    @NotNull(message = "Categoria é obrigatória")
    private UUID categoryId;

    @Size(max = 200, message = "Descrição deve ter no máximo 200 caracteres")
    private String description;

    @NotNull(message = "Data é obrigatória")
    @PastOrPresent(message = "Data não pode ser futura")
    private LocalDate transactionDate;
}
