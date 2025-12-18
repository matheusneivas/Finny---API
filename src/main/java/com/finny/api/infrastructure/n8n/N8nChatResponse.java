package com.finny.api.infrastructure.n8n;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class N8nChatResponse {
    private String reply;
    private Integer tokensUsed;
    private String workflowId;
}
