package com.gubee.stockreconciliation.infrastructure.adapter.in.web.dto;

import com.gubee.stockreconciliation.domain.model.EventStatus;
import com.gubee.stockreconciliation.domain.model.EventType;

import java.time.LocalDateTime;

public record StockEventResponse(
    String eventId,
    EventType type,
    EventStatus status,
    String statusReason,
    String accountId,
    String sku,
    String marketplace,
    String externalOrderId,
    LocalDateTime occurredAt,
    LocalDateTime processedAt
) {}
