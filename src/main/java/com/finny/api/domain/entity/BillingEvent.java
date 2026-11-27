package com.finny.api.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Pagamentos já creditados ("pix:&lt;id&gt;", "card:&lt;id&gt;"). O gateway reenvia avisos; com esta
 * tabela cada pagamento soma Premium uma vez só.
 */
@Entity
@Table(name = "billing_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingEvent {

    @Id
    @Column(length = 100)
    private String id;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;
}
