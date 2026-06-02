package com.gubee.stockreconciliation.application.port.in;

import com.gubee.stockreconciliation.infrastructure.adapter.in.web.dto.StockHistoryEntryResponse;
import com.gubee.stockreconciliation.infrastructure.adapter.in.web.dto.StockResponse;

import java.util.List;

public interface QueryStockUseCase {
    StockResponse getCurrentStock(String accountId, String sku);
    List<StockHistoryEntryResponse> getHistory(String accountId, String sku);
}
