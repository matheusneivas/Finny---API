package com.finny.api.application.dto.response;

import com.finny.api.domain.enums.ProcessingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrStatusResponse {
    private UUID processingId;
    private ProcessingStatus status;
    private String errorMessage;
    private TransactionResponse createdTransaction;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
}
