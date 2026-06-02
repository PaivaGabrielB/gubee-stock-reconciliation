package com.gubee.stockreconciliation.application.port.in;

import com.gubee.stockreconciliation.domain.model.EventStatus;
import com.gubee.stockreconciliation.infrastructure.adapter.in.web.dto.InconsistencyResponse;
import com.gubee.stockreconciliation.infrastructure.adapter.in.web.dto.StockEventResponse;

import java.util.List;

public interface QueryEventsUseCase {
    List<StockEventResponse> getEventsByStatus(EventStatus status);
    List<InconsistencyResponse> getInconsistencies();
}
