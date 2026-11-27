package com.finny.api.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Resultado guardado de uma criação feita com Idempotency-Key. */
@Entity
@Table(name = "idempotency_keys",
    uniqueConstraints = @UniqueConstraint(name = "uk_idempotency_user_key", columnNames = {"user_id", "idem_key"}),
    indexes = @Index(name = "idx_idempotency_created", columnList = "created_at"))
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyRecord {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "idem_key", nullable = false, length = 64)
    private String key;

    @Column(nullable = false, length = 120)
    private String scope;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column(name = "response_body", nullable = false, columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
