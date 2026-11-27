package com.finny.api.domain.entity;

import com.finny.api.domain.enums.ProposedActionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Estado de uma ação proposta pela IA (o conteúdo fica em chat_messages.proposed_actions). */
@Entity
@Table(name = "chat_actions", indexes = @Index(name = "idx_chat_actions_message", columnList = "message_id"))
@Getter
@Setter
@NoArgsConstructor
public class ChatAction {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProposedActionStatus status;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;
}
