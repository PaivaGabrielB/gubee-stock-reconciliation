package com.gubee.stockreconciliation.application.service;

import com.gubee.stockreconciliation.application.port.in.QueryStockUseCase;
import com.gubee.stockreconciliation.application.port.out.StockHistoryRepositoryPort;
import com.gubee.stockreconciliation.application.port.out.StockRepositoryPort;
import com.gubee.stockreconciliation.domain.exception.StockNotFoundException;
import com.gubee.stockreconciliation.domain.model.Stock;
import com.gubee.stockreconciliation.infrastructure.adapter.in.web.dto.StockHistoryEntryResponse;
import com.gubee.stockreconciliation.infrastructure.adapter.in.web.dto.StockResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockQueryService implements QueryStockUseCase {

    private final StockRepositoryPort stockRepo;
    private final StockHistoryRepositoryPort historyRepo;

    @Override
    public StockResponse getCurrentStock(String accountId, String sku) {
        Stock stock = stockRepo.findByAccountIdAndSku(accountId, sku)
            .orElseThrow(() -> new StockNotFoundException(accountId, sku));
        return new StockResponse(stock.getAccountId(), stock.getSku(),
            stock.getAvailableQuantity(), stock.getLastUpdatedAt());
    }

    @Override
    public List<StockHistoryEntryResponse> getHistory(String accountId, String sku) {
        return historyRepo.findByAccountIdAndSkuOrderByProcessedAtAsc(accountId, sku)
            .stream()
            .map(h -> new StockHistoryEntryResponse(
                h.getEventId(), h.getEventType(),
                h.getQuantityBefore(), h.getQuantityAfter(), h.getDelta(),
                h.getMarketplace(), h.getExternalOrderId(), h.getReason(),
                h.getOccurredAt(), h.getProcessedAt()))
            .toList();
    }
}
