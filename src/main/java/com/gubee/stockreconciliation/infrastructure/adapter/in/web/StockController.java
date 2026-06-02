package com.gubee.stockreconciliation.infrastructure.adapter.in.web;

import com.gubee.stockreconciliation.application.port.in.QueryStockUseCase;
import com.gubee.stockreconciliation.infrastructure.adapter.in.web.dto.StockHistoryEntryResponse;
import com.gubee.stockreconciliation.infrastructure.adapter.in.web.dto.StockResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/stocks")
@RequiredArgsConstructor
@Tag(name = "Stock", description = "Query current stock and history")
public class StockController {

    private final QueryStockUseCase queryStockUseCase;

    @GetMapping("/{accountId}/{sku}")
    @Operation(summary = "Get current stock for account + SKU")
    public StockResponse getCurrentStock(@PathVariable String accountId,
                                          @PathVariable String sku) {
        return queryStockUseCase.getCurrentStock(accountId, sku);
    }

    @GetMapping("/{accountId}/{sku}/history")
    @Operation(summary = "Get stock change history for account + SKU")
    public List<StockHistoryEntryResponse> getHistory(@PathVariable String accountId,
                                                       @PathVariable String sku) {
        return queryStockUseCase.getHistory(accountId, sku);
    }
}
