package com.finny.api.application.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
public class OcrCallbackRequest {
    private BigDecimal amount;
    private String description;
    private LocalDate date;
    private String categoryName;
    private String rawData;
}
