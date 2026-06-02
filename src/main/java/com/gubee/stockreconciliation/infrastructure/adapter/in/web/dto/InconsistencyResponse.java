package com.gubee.stockreconciliation.infrastructure.adapter.in.web.dto;

import com.gubee.stockreconciliation.domain.model.EventType;

import java.time.LocalDateTime;
import java.util.UUID;

public record InconsistencyResponse(
    UUID id,
    String eventId,
    EventType eventType,
    String accountId,
    String sku,
    String marketplace,
    String externalOrderId,
    String description,
    LocalDateTime occurredAt,
    LocalDateTime detectedAt
) {}
