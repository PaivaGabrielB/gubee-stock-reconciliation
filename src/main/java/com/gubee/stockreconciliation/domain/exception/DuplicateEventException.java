package com.gubee.stockreconciliation.domain.exception;

public class DuplicateEventException extends RuntimeException {
    public DuplicateEventException(String eventId) {
        super("Duplicate eventId: " + eventId);
    }
}
