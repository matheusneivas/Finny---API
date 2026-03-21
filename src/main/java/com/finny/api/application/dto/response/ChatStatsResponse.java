package com.finny.api.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatStatsResponse {
    private Long totalMessages;
    private Long totalTokensUsed;
    private Double estimatedCostUsd;
}
