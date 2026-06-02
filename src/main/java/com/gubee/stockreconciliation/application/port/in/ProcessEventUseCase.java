package com.gubee.stockreconciliation.application.port.in;

import com.gubee.stockreconciliation.infrastructure.adapter.in.web.dto.EventRequest;
import com.gubee.stockreconciliation.infrastructure.adapter.in.web.dto.EventResponse;

public interface ProcessEventUseCase {
    EventResponse process(EventRequest request);
}
