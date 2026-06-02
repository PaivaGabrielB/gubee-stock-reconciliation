package com.gubee.stockreconciliation.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "inconsistencies")
@Getter @Setter @NoArgsConstructor
public class Inconsistency {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "sku", nullable = false)
    private String sku;

    @Column(name = "marketplace")
    private String marketplace;

    @Column(name = "external_order_id")
    private String externalOrderId;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    public static Inconsistency of(String eventId, EventType type, String accountId, String sku,
                                    String marketplace, String externalOrderId,
                                    String description, LocalDateTime occurredAt) {
        Inconsistency i = new Inconsistency();
        i.eventId = eventId;
        i.eventType = type;
        i.accountId = accountId;
        i.sku = sku;
        i.marketplace = marketplace;
        i.externalOrderId = externalOrderId;
        i.description = description;
        i.occurredAt = occurredAt;
        i.detectedAt = LocalDateTime.now();
        return i;
    }
}
