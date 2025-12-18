package com.finny.api.infrastructure.n8n;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class N8nOcrRequest {
    private String userId;
    private String imageUrl;
    private String callbackUrl;
    private String callbackToken;
}
