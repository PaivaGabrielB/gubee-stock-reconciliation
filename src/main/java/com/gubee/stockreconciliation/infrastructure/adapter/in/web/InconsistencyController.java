package com.gubee.stockreconciliation.infrastructure.adapter.in.web;

import com.gubee.stockreconciliation.application.port.in.QueryEventsUseCase;
import com.gubee.stockreconciliation.infrastructure.adapter.in.web.dto.InconsistencyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/inconsistencies")
@RequiredArgsConstructor
@Tag(name = "Inconsistencies", description = "Query problematic events")
public class InconsistencyController {

    private final QueryEventsUseCase queryEventsUseCase;

    @GetMapping
    @Operation(summary = "List all detected inconsistencies")
    public List<InconsistencyResponse> getInconsistencies() {
        return queryEventsUseCase.getInconsistencies();
    }
}
