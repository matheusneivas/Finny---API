package com.finny.api.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Bytes da foto de perfil, separados de users para não carregar a imagem a cada consulta. */
@Entity
@Table(name = "user_avatars")
@Getter
@Setter
@NoArgsConstructor
public class UserAvatar {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;

    @Column(nullable = false, length = 50)
    private String contentType;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;
}
