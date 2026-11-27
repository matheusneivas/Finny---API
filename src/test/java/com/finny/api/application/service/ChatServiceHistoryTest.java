package com.finny.api.application.service;

import com.finny.api.infrastructure.ai.AiMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatServiceHistoryTest {

    @Test
    void historyStartsWithUserAndAlternatesRoles() {
        List<AiMessage> normalized = ChatService.normalizeHistory(List.of(
            AiMessage.assistant("resposta antiga sem pergunta"),
            AiMessage.user("oi"),
            AiMessage.user("tudo bem?"),
            AiMessage.assistant(""),
            AiMessage.assistant("tudo!"),
            AiMessage.user("gastei 10")));

        assertThat(normalized).extracting(AiMessage::role)
            .containsExactly(AiMessage.Role.USER, AiMessage.Role.ASSISTANT, AiMessage.Role.USER);
        assertThat(normalized.get(0).content()).isEqualTo("oi\n\ntudo bem?");
    }
}
