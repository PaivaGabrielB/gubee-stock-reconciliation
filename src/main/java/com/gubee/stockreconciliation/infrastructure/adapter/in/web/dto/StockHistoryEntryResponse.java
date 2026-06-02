package com.gubee.stockreconciliation.infrastructure.adapter.in.web.dto;

import com.gubee.stockreconciliation.domain.model.EventType;

import java.time.LocalDateTime;

public record StockHistoryEntryResponse(
    String eventId,
    EventType eventType,
    int quantityBefore,
    int quantityAfter,
    int delta,
    String marketplace,
    String externalOrderId,
    String reason,
    LocalDateTime occurredAt,
    LocalDateTime processedAt
) {}
