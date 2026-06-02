package com.gubee.stockreconciliation.domain.exception;

public class StockNotFoundException extends RuntimeException {
    public StockNotFoundException(String accountId, String sku) {
        super("Stock not found for accountId=" + accountId + " sku=" + sku);
    }
}
