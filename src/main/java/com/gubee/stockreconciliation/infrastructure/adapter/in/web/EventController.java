package com.gubee.stockreconciliation.infrastructure.adapter.in.web;

import com.gubee.stockreconciliation.application.port.in.ProcessEventUseCase;
import com.gubee.stockreconciliation.application.port.in.QueryEventsUseCase;
import com.gubee.stockreconciliation.domain.model.EventStatus;
import com.gubee.stockreconciliation.infrastructure.adapter.in.web.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Events", description = "Receive and query stock/order events")
public class EventController {

    private final ProcessEventUseCase processEventUseCase;
    private final QueryEventsUseCase queryEventsUseCase;

    @PostMapping("/events")
    @Operation(summary = "Submit a stock or order event")
    public ResponseEntity<EventResponse> receiveEvent(@Valid @RequestBody EventRequest request) {
        EventResponse response = processEventUseCase.process(request);
        int httpStatus = switch (response.status()) {
            case PROCESSED -> 200;
            case IGNORED   -> 200;
            case PENDING   -> 202;
            case INCONSISTENT -> 422;
            default -> 200;
        };
        return ResponseEntity.status(httpStatus).body(response);
    }

    @GetMapping("/events")
    @Operation(summary = "Query events by status (PENDING, INCONSISTENT, PROCESSED, etc.)")
    public List<StockEventResponse> getEvents(
            @RequestParam(required = false, defaultValue = "PENDING") EventStatus status) {
        return queryEventsUseCase.getEventsByStatus(status);
    }
}
