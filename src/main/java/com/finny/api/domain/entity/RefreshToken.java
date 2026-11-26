package com.finny.api.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token emitido (guardamos só o jti, nunca o token).
 * Todos os tokens de um mesmo login compartilham a {@code familyId}: se um token já
 * usado aparecer de novo (possível roubo), a família inteira é revogada.
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_refresh_tokens_user", columnList = "user_id"),
    @Index(name = "idx_refresh_tokens_family", columnList = "family_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    /** jti do JWT. */
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public boolean isRevoked() {
        return revokedAt != null;
    }
}
