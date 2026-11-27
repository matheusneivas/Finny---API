package com.finny.api.domain.entity;

import com.finny.api.domain.enums.EmailCodePurpose;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Código de 6 dígitos enviado por e-mail. Só o HMAC fica no banco. */
@Entity
@Table(name = "email_codes", indexes = @Index(name = "idx_email_codes_user_purpose", columnList = "user_id, purpose, created_at"))
@Getter
@Setter
@NoArgsConstructor
public class EmailCode {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailCodePurpose purpose;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;
}
