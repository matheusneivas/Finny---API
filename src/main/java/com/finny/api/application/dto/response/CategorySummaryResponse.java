package com.finny.api.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategorySummaryResponse {
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalExpense;
    private List<CategoryItem> categories;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryItem {
        private String categoryId;
        private String categoryName;
        private String categoryIcon;
        private String categoryColor;
        private BigDecimal total;
        private Double percentage;
        private Long transactionCount;
    }
}
