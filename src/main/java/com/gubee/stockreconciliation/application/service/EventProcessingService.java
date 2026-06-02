package com.gubee.stockreconciliation.application.service;

import com.gubee.stockreconciliation.application.port.in.ProcessEventUseCase;
import com.gubee.stockreconciliation.application.port.out.*;
import com.gubee.stockreconciliation.domain.model.*;
import com.gubee.stockreconciliation.infrastructure.adapter.in.web.dto.EventRequest;
import com.gubee.stockreconciliation.infrastructure.adapter.in.web.dto.EventResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventProcessingService implements ProcessEventUseCase {

    private final StockRepositoryPort stockRepo;
    private final StockEventRepositoryPort stockEventRepo;
    private final StockHistoryRepositoryPort historyRepo;
    private final OrderRepositoryPort orderRepo;
    private final InconsistencyRepositoryPort inconsistencyRepo;
    private final EventPublisherPort eventPublisher;

    @Override
    @Transactional
    public EventResponse process(EventRequest request) {
        if (stockEventRepo.existsByEventId(request.getEventId())) {
            log.info("event_id={} ignored=duplicate_event_id", request.getEventId());
            return EventResponse.of(request.getEventId(), EventStatus.IGNORED, "Duplicate eventId — already processed");
        }

        StockEvent event = buildEvent(request);
        stockEventRepo.save(event);

        ProcessingResult result = switch (request.getType()) {
            case ORDER_CREATED              -> handleOrderCreated(request);
            case ORDER_CANCELLED            -> handleOrderCancelled(request);
            case STOCK_ADJUSTED             -> handleStockAdjusted(request);
            case STOCK_SYNC_SENT            -> handleStockSyncSent(request);
            case MARKETPLACE_STOCK_RESTORED -> handleMarketplaceStockRestored(request);
        };

        event.setStatus(result.status());
        event.setStatusReason(result.reason());
        event.setProcessedAt(LocalDateTime.now());
        stockEventRepo.save(event);

        publishSafely(event);

        log.info("event_id={} type={} status={}", request.getEventId(), request.getType(), result.status());
        return EventResponse.of(request.getEventId(), result.status(), result.reason());
    }

    // ── ORDER_CREATED ──────────────────────────────────────────────────────────

    private ProcessingResult handleOrderCreated(EventRequest req) {
        Optional<Order> existing = orderRepo.findByKey(
            req.getMarketplace(), req.getAccountId(), req.getExternalOrderId(), req.getSku());

        if (existing.isPresent()) {
            recordInconsistency(req, "Logical duplicate: ORDER_CREATED already exists for this order");
            return ProcessingResult.inconsistent("Order already registered");
        }

        Stock stock = lockOrCreateStock(req.getAccountId(), req.getSku());
        int before = stock.getAvailableQuantity();
        int after  = before - req.getQuantity();

        if (after < 0) {
            recordInconsistency(req,
                "Insufficient stock: available=" + before + " required=" + req.getQuantity());
            return ProcessingResult.inconsistent(
                "Insufficient stock: available=" + before + " required=" + req.getQuantity());
        }

        stock.setAvailableQuantity(after);
        stock.setLastUpdatedAt(LocalDateTime.now());
        stockRepo.save(stock);

        orderRepo.save(Order.create(
            req.getMarketplace(), req.getAccountId(),
            req.getExternalOrderId(), req.getSku(), req.getQuantity()));

        saveHistory(req, before, after, req.getType());
        resolvePendingCancellations(req);

        return ProcessingResult.processed("Stock deducted: " + before + " → " + after);
    }

    // ── ORDER_CANCELLED ────────────────────────────────────────────────────────

    private ProcessingResult handleOrderCancelled(EventRequest req) {
        Optional<Order> orderOpt = orderRepo.findByKey(
            req.getMarketplace(), req.getAccountId(), req.getExternalOrderId(), req.getSku());

        if (orderOpt.isEmpty()) {
            recordInconsistency(req, "ORDER_CANCELLED received before ORDER_CREATED — saved as PENDING");
            return ProcessingResult.pending("No matching ORDER_CREATED found — event is pending resolution");
        }

        Order order = orderOpt.get();

        if (order.getStatus() == OrderStatus.CANCELLED) {
            recordInconsistency(req, "Duplicate cancellation: order already cancelled");
            return ProcessingResult.inconsistent("Order already cancelled — duplicate cancellation ignored");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());

        int before = 0, after = 0;
        if (!order.getStockRestored()) {
            Stock stock = lockOrCreateStock(req.getAccountId(), req.getSku());
            before = stock.getAvailableQuantity();
            after  = before + order.getQuantity();
            stock.setAvailableQuantity(after);
            stock.setLastUpdatedAt(LocalDateTime.now());
            stockRepo.save(stock);
            order.setStockRestored(true);
            saveHistory(req, before, after, req.getType());
        }

        orderRepo.save(order);
        return ProcessingResult.processed("Order cancelled, stock restored: " + before + " → " + after);
    }

    // ── STOCK_ADJUSTED ─────────────────────────────────────────────────────────

    private ProcessingResult handleStockAdjusted(EventRequest req) {
        Stock stock = lockOrCreateStock(req.getAccountId(), req.getSku());
        int before = stock.getAvailableQuantity();
        int after  = req.getAvailable();

        stock.setAvailableQuantity(after);
        stock.setLastUpdatedAt(LocalDateTime.now());
        stockRepo.save(stock);

        saveHistory(req, before, after, req.getType());
        return ProcessingResult.processed("Stock adjusted: " + before + " → " + after);
    }

    // ── STOCK_SYNC_SENT ────────────────────────────────────────────────────────

    private ProcessingResult handleStockSyncSent(EventRequest req) {
        int current = stockRepo.findByAccountIdAndSku(req.getAccountId(), req.getSku())
            .map(Stock::getAvailableQuantity).orElse(0);

        StockHistory h = new StockHistory();
        h.setAccountId(req.getAccountId());
        h.setSku(req.getSku());
        h.setEventId(req.getEventId());
        h.setEventType(req.getType());
        h.setQuantityBefore(current);
        h.setQuantityAfter(current);
        h.setDelta(0);
        h.setMarketplace(req.getMarketplace());
        h.setReason("sync_sent qty=" + req.getQuantitySent());
        h.setOccurredAt(req.getOccurredAt());
        h.setProcessedAt(LocalDateTime.now());
        historyRepo.save(h);

        return ProcessingResult.processed("Sync recorded — no stock change");
    }

    // ── MARKETPLACE_STOCK_RESTORED ─────────────────────────────────────────────

    private ProcessingResult handleMarketplaceStockRestored(EventRequest req) {
        Optional<Order> orderOpt = orderRepo.findByKey(
            req.getMarketplace(), req.getAccountId(), req.getExternalOrderId(), req.getSku());

        if (orderOpt.isEmpty()) {
            recordInconsistency(req,
                "MARKETPLACE_STOCK_RESTORED without corresponding order — cannot reconcile");
            return ProcessingResult.inconsistent("No matching order found");
        }

        Order order = orderOpt.get();

        if (order.getStockRestored()) {
            recordInconsistency(req, "Stock already restored for order " + req.getExternalOrderId()
                + " — duplicate MARKETPLACE_STOCK_RESTORED ignored");
            return ProcessingResult.inconsistent("Stock already restored for this order");
        }

        Stock stock = lockOrCreateStock(req.getAccountId(), req.getSku());
        int before = stock.getAvailableQuantity();
        int after  = before + req.getQuantity();

        stock.setAvailableQuantity(after);
        stock.setLastUpdatedAt(LocalDateTime.now());
        stockRepo.save(stock);

        order.setStockRestored(true);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepo.save(order);

        saveHistory(req, before, after, req.getType());
        return ProcessingResult.processed("Marketplace restored stock: " + before + " → " + after);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private Stock lockOrCreateStock(String accountId, String sku) {
        return stockRepo.findByAccountIdAndSkuWithLock(accountId, sku)
            .orElseGet(() -> stockRepo.save(Stock.create(accountId, sku)));
    }

    private void saveHistory(EventRequest req, int before, int after, EventType type) {
        StockHistory h = new StockHistory();
        h.setAccountId(req.getAccountId());
        h.setSku(req.getSku());
        h.setEventId(req.getEventId());
        h.setEventType(type);
        h.setQuantityBefore(before);
        h.setQuantityAfter(after);
        h.setDelta(after - before);
        h.setMarketplace(req.getMarketplace());
        h.setExternalOrderId(req.getExternalOrderId());
        h.setReason(req.getReason());
        h.setOccurredAt(req.getOccurredAt());
        h.setProcessedAt(LocalDateTime.now());
        historyRepo.save(h);
    }

    private void recordInconsistency(EventRequest req, String description) {
        inconsistencyRepo.save(Inconsistency.of(
            req.getEventId(), req.getType(), req.getAccountId(), req.getSku(),
            req.getMarketplace(), req.getExternalOrderId(), description, req.getOccurredAt()));
    }

    private StockEvent buildEvent(EventRequest req) {
        StockEvent e = new StockEvent();
        e.setEventId(req.getEventId());
        e.setType(req.getType());
        e.setStatus(EventStatus.PROCESSING);
        e.setAccountId(req.getAccountId());
        e.setSku(req.getSku());
        e.setMarketplace(req.getMarketplace());
        e.setExternalOrderId(req.getExternalOrderId());
        e.setQuantity(req.getQuantity());
        e.setAvailable(req.getAvailable());
        e.setReason(req.getReason());
        e.setQuantitySent(req.getQuantitySent());
        e.setOccurredAt(req.getOccurredAt());
        return e;
    }

    private void resolvePendingCancellations(EventRequest createReq) {
        List<StockEvent> pending = stockEventRepo.findPendingCancellations(
            createReq.getMarketplace(), createReq.getAccountId(),
            createReq.getExternalOrderId(), createReq.getSku());

        if (pending.isEmpty()) return;

        StockEvent pendingEvent = pending.get(0);
        EventRequest cancelReq = toRequest(pendingEvent);
        ProcessingResult result = handleOrderCancelled(cancelReq);

        pendingEvent.setStatus(result.status());
        pendingEvent.setStatusReason("Auto-resolved after ORDER_CREATED arrived late");
        pendingEvent.setProcessedAt(LocalDateTime.now());
        stockEventRepo.save(pendingEvent);

        log.info("event_id={} auto_resolved_pending_cancel", pendingEvent.getEventId());
    }

    private EventRequest toRequest(StockEvent e) {
        EventRequest r = new EventRequest();
        r.setEventId(e.getEventId());
        r.setType(e.getType());
        r.setOccurredAt(e.getOccurredAt());
        r.setMarketplace(e.getMarketplace());
        r.setAccountId(e.getAccountId());
        r.setExternalOrderId(e.getExternalOrderId());
        r.setSku(e.getSku());
        r.setQuantity(e.getQuantity());
        r.setAvailable(e.getAvailable());
        r.setReason(e.getReason());
        r.setQuantitySent(e.getQuantitySent());
        return r;
    }

    private void publishSafely(StockEvent event) {
        try {
            eventPublisher.publish(event);
        } catch (Exception ex) {
            log.warn("kafka_publish_failed event_id={} error={}", event.getEventId(), ex.getMessage());
        }
    }

    // ── internal result type ───────────────────────────────────────────────────

    record ProcessingResult(EventStatus status, String reason) {
        static ProcessingResult processed(String reason)     { return new ProcessingResult(EventStatus.PROCESSED, reason); }
        static ProcessingResult inconsistent(String reason)  { return new ProcessingResult(EventStatus.INCONSISTENT, reason); }
        static ProcessingResult pending(String reason)       { return new ProcessingResult(EventStatus.PENDING, reason); }
    }
}
