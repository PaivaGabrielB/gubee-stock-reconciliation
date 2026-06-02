package com.gubee.stockreconciliation.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_events")
@Getter @Setter @NoArgsConstructor
public class StockEvent {

    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private EventType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EventStatus status;

    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "sku", nullable = false)
    private String sku;

    @Column(name = "marketplace")
    private String marketplace;

    @Column(name = "external_order_id")
    private String externalOrderId;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "available")
    private Integer available;

    @Column(name = "quantity_sent")
    private Integer quantitySent;

    @Column(name = "reason")
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
