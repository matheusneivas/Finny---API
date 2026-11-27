package com.finny.api.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Quem já usou o teste grátis. A chave é o HMAC do CPF (um teste por pessoa, mesmo criando
 * outra conta) ou "user:&lt;id&gt;" para contas antigas sem CPF.
 */
@Entity
@Table(name = "billing_trials")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingTrial {

    @Id
    @Column(name = "trial_key", length = 64)
    private String trialKey;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
