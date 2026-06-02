package com.gubee.stockreconciliation.infrastructure.adapter.in.web.dto;

import com.gubee.stockreconciliation.domain.model.EventStatus;

public record EventResponse(
    String eventId,
    EventStatus status,
    String message
) {
    public static EventResponse of(String eventId, EventStatus status, String message) {
        return new EventResponse(eventId, status, message);
    }
}
