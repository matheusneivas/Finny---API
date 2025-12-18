package com.finny.api.application.dto.response;

import com.finny.api.domain.enums.GoalStatus;
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
public class GoalResponse {
    private UUID id;
    private String name;
    private BigDecimal targetAmount;
    private BigDecimal savedAmount;
    private LocalDate deadline;
    private CategoryResponse relatedCategory;
    private String icon;
    private GoalStatus status;
    private BigDecimal progressPercentage;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
