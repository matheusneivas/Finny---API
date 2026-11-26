package com.finny.api.infrastructure.ai;

import java.util.Map;

@FunctionalInterface
public interface AiToolHandler {

    AiToolResult handle(String toolName, Map<String, Object> input);
}
