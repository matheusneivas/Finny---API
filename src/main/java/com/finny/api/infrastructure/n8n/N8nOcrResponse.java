package com.finny.api.infrastructure.n8n;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class N8nOcrResponse {
    private BigDecimal amount;
    private String description;
    private LocalDate date;
    private String categoryName;
    private String rawData;
}
