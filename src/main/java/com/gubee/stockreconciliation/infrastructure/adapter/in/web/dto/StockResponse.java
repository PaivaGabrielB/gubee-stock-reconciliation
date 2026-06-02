package com.gubee.stockreconciliation.infrastructure.adapter.in.web.dto;

import java.time.LocalDateTime;

public record StockResponse(
    String accountId,
    String sku,
    int availableQuantity,
    LocalDateTime lastUpdatedAt
) {}
