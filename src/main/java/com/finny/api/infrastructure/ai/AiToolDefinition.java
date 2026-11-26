package com.finny.api.infrastructure.ai;

import java.util.List;
import java.util.Map;

/**
 * Definição neutra de ferramenta (JSON Schema), convertida por cada provedor.
 */
public record AiToolDefinition(
    String name,
    String description,
    Map<String, Object> properties,
    List<String> required
) {}
