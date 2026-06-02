package com.gubee.stockreconciliation.application.service;

import com.gubee.stockreconciliation.application.port.in.QueryEventsUseCase;
import com.gubee.stockreconciliation.application.port.out.InconsistencyRepositoryPort;
import com.gubee.stockreconciliation.application.port.out.StockEventRepositoryPort;
import com.gubee.stockreconciliation.domain.model.EventStatus;
import com.gubee.stockreconciliation.infrastructure.adapter.in.web.dto.InconsistencyResponse;
import com.gubee.stockreconciliation.infrastructure.adapter.in.web.dto.StockEventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventQueryService implements QueryEventsUseCase {

    private final StockEventRepositoryPort stockEventRepo;
    private final InconsistencyRepositoryPort inconsistencyRepo;

    @Override
    public List<StockEventResponse> getEventsByStatus(EventStatus status) {
        return stockEventRepo.findByStatus(status).stream()
            .map(e -> new StockEventResponse(
                e.getEventId(), e.getType(), e.getStatus(), e.getStatusReason(),
                e.getAccountId(), e.getSku(), e.getMarketplace(), e.getExternalOrderId(),
                e.getOccurredAt(), e.getProcessedAt()))
            .toList();
    }

    @Override
    public List<InconsistencyResponse> getInconsistencies() {
        return inconsistencyRepo.findAll().stream()
            .map(i -> new InconsistencyResponse(
                i.getId(), i.getEventId(), i.getEventType(),
                i.getAccountId(), i.getSku(), i.getMarketplace(), i.getExternalOrderId(),
                i.getDescription(), i.getOccurredAt(), i.getDetectedAt()))
            .toList();
    }
}
