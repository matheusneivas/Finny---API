package com.finny.api.infrastructure.ai;

public record AiMessage(Role role, String content) {

    public enum Role { USER, ASSISTANT }

    public static AiMessage user(String content) {
        return new AiMessage(Role.USER, content);
    }

    public static AiMessage assistant(String content) {
        return new AiMessage(Role.ASSISTANT, content);
    }
}
