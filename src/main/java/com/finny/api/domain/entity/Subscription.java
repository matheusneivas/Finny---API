package com.finny.api.domain.entity;

import com.finny.api.domain.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Assinatura no cartão (uma linha por usuário, reaproveitada ao assinar de novo). */
@Entity
@Table(name = "subscriptions")
@Data
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    /** Gateway de pagamento (ex.: "mercadopago"). */
    @Column(nullable = false, length = 20)
    private String provider;

    /** Id da assinatura no gateway (preapproval do Mercado Pago). */
    @Column(name = "external_id", length = 64, unique = true)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SubscriptionStatus status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** Página do gateway onde a pessoa cadastra o cartão. */
    @Column(name = "checkout_url", length = 500)
    private String checkoutUrl;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
