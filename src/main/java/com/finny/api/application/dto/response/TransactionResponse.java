package com.finny.api.application.dto.response;

import com.finny.api.domain.enums.TransactionSource;
import com.finny.api.domain.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    private UUID id;
    private BigDecimal amount;
    private TransactionType type;
    private CategoryResponse category;
    private String description;
    private LocalDate transactionDate;
    private TransactionSource source;
    private String ocrImageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
