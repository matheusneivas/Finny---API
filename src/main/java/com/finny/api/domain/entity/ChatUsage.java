package com.finny.api.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Contador de mensagens enviadas pelo usuário por mês ("2026-11").
 * Fica separado do histórico para que apagar a conversa não zere o limite.
 */
@Entity
@Table(name = "chat_usage",
    uniqueConstraints = @UniqueConstraint(name = "uk_chat_usage_user_period", columnNames = {"user_id", "period"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 7)
    private String period;

    @Column(name = "message_count", nullable = false)
    private int messageCount;
}
