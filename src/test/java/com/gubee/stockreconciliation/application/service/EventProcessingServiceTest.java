package com.gubee.stockreconciliation.application.service;

import com.gubee.stockreconciliation.application.port.out.*;
import com.gubee.stockreconciliation.domain.model.*;
import com.gubee.stockreconciliation.infrastructure.adapter.in.web.dto.EventRequest;
import com.gubee.stockreconciliation.infrastructure.adapter.in.web.dto.EventResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
//@MockitoSettings(strictness = Strictness.LENIENT)
class EventProcessingServiceTest {

    @Mock StockRepositoryPort stockRepo;
    @Mock StockEventRepositoryPort stockEventRepo;
    @Mock StockHistoryRepositoryPort historyRepo;
    @Mock OrderRepositoryPort orderRepo;
    @Mock InconsistencyRepositoryPort inconsistencyRepo;
    @Mock EventPublisherPort eventPublisher;

    @InjectMocks EventProcessingService service;

    private static final String ACCOUNT_ID = "account-001";
    private static final String SKU = "ABC-123";
    private static final String MARKETPLACE = "MERCADO_LIVRE";
    private static final String ORDER_ID = "ML-123456";

    /*@BeforeEach
    void setup() {
        when(stockEventRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inconsistencyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(eventPublisher).publish(any());
    }*/

    @BeforeEach
    void setup() {
        lenient().when(stockEventRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(stockRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(historyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(inconsistencyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().doNothing().when(eventPublisher).publish(any());
    }

    // ─── Idempotency ──────────────────────────────────────────────────────────

    @Test
    void duplicateEventId_shouldReturnIgnored() {
        when(stockEventRepo.existsByEventId("evt-dup")).thenReturn(true);

        EventResponse resp = service.process(buildAdjust("evt-dup", 10));

        assertThat(resp.status()).isEqualTo(EventStatus.IGNORED);
        verify(stockRepo, never()).save(any());
    }

    // ─── STOCK_ADJUSTED ───────────────────────────────────────────────────────

    @Test
    void stockAdjusted_noExistingStock_createsAndSetsQuantity() {
        when(stockEventRepo.existsByEventId(anyString())).thenReturn(false);
        when(stockRepo.findByAccountIdAndSkuWithLock(ACCOUNT_ID, SKU)).thenReturn(Optional.empty());

        EventResponse resp = service.process(buildAdjust("evt-001", 10));

        assertThat(resp.status()).isEqualTo(EventStatus.PROCESSED);
        verify(stockRepo, atLeastOnce()).save(argThat(s -> s.getAvailableQuantity() == 10));
    }

    @Test
    void stockAdjusted_existingStock_overridesWithAbsoluteValue() {
        when(stockEventRepo.existsByEventId(anyString())).thenReturn(false);
        Stock existing = Stock.create(ACCOUNT_ID, SKU);
        existing.setAvailableQuantity(5);
        when(stockRepo.findByAccountIdAndSkuWithLock(ACCOUNT_ID, SKU)).thenReturn(Optional.of(existing));

        EventResponse resp = service.process(buildAdjust("evt-001", 20));

        assertThat(resp.status()).isEqualTo(EventStatus.PROCESSED);
        verify(stockRepo).save(argThat(s -> s.getAvailableQuantity() == 20));
    }

    // ─── ORDER_CREATED ────────────────────────────────────────────────────────

    @Test
    void orderCreated_deductsStock() {
        when(stockEventRepo.existsByEventId(anyString())).thenReturn(false);
        when(orderRepo.findByKey(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        Stock stock = Stock.create(ACCOUNT_ID, SKU);
        stock.setAvailableQuantity(10);
        when(stockRepo.findByAccountIdAndSkuWithLock(ACCOUNT_ID, SKU)).thenReturn(Optional.of(stock));
        when(stockEventRepo.findPendingCancellations(any(), any(), any(), any()))
            .thenReturn(List.of());

        EventResponse resp = service.process(buildOrderCreated("evt-001", 2));

        assertThat(resp.status()).isEqualTo(EventStatus.PROCESSED);
        verify(stockRepo).save(argThat(s -> s.getAvailableQuantity() == 8));
    }

    @Test
    void orderCreated_insufficientStock_returnsInconsistent() {
        when(stockEventRepo.existsByEventId(anyString())).thenReturn(false);
        when(orderRepo.findByKey(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        Stock stock = Stock.create(ACCOUNT_ID, SKU);
        stock.setAvailableQuantity(1);
        when(stockRepo.findByAccountIdAndSkuWithLock(ACCOUNT_ID, SKU)).thenReturn(Optional.of(stock));

        EventResponse resp = service.process(buildOrderCreated("evt-001", 5));

        assertThat(resp.status()).isEqualTo(EventStatus.INCONSISTENT);
        verify(inconsistencyRepo).save(any());
        verify(stockRepo, never()).save(argThat(s -> s.getAvailableQuantity() < 0));
    }

    @Test
    void orderCreated_logicalDuplicate_returnsInconsistent() {
        when(stockEventRepo.existsByEventId(anyString())).thenReturn(false);
        Order existing = Order.create(MARKETPLACE, ACCOUNT_ID, ORDER_ID, SKU, 2);
        when(orderRepo.findByKey(MARKETPLACE, ACCOUNT_ID, ORDER_ID, SKU))
            .thenReturn(Optional.of(existing));

        EventResponse resp = service.process(buildOrderCreated("evt-002", 2));

        assertThat(resp.status()).isEqualTo(EventStatus.INCONSISTENT);
    }

    // ─── ORDER_CANCELLED ──────────────────────────────────────────────────────

    @Test
    void orderCancelled_restoresStock() {
        when(stockEventRepo.existsByEventId(anyString())).thenReturn(false);
        Order order = Order.create(MARKETPLACE, ACCOUNT_ID, ORDER_ID, SKU, 2);
        when(orderRepo.findByKey(MARKETPLACE, ACCOUNT_ID, ORDER_ID, SKU))
            .thenReturn(Optional.of(order));
        Stock stock = Stock.create(ACCOUNT_ID, SKU);
        stock.setAvailableQuantity(8);
        when(stockRepo.findByAccountIdAndSkuWithLock(ACCOUNT_ID, SKU)).thenReturn(Optional.of(stock));

        EventResponse resp = service.process(buildOrderCancelled("evt-002"));

        assertThat(resp.status()).isEqualTo(EventStatus.PROCESSED);
        verify(stockRepo).save(argThat(s -> s.getAvailableQuantity() == 10));
    }

    @Test
    void orderCancelled_duplicateCancellation_returnsInconsistent() {
        when(stockEventRepo.existsByEventId(anyString())).thenReturn(false);
        Order order = Order.create(MARKETPLACE, ACCOUNT_ID, ORDER_ID, SKU, 2);
        order.setStatus(OrderStatus.CANCELLED);
        when(orderRepo.findByKey(MARKETPLACE, ACCOUNT_ID, ORDER_ID, SKU))
            .thenReturn(Optional.of(order));

        EventResponse resp = service.process(buildOrderCancelled("evt-003"));

        assertThat(resp.status()).isEqualTo(EventStatus.INCONSISTENT);
        verify(stockRepo, never()).save(any());
    }

    @Test
    void orderCancelled_beforeCreated_returnsPending() {
        when(stockEventRepo.existsByEventId(anyString())).thenReturn(false);
        when(orderRepo.findByKey(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());

        EventResponse resp = service.process(buildOrderCancelled("evt-002"));

        assertThat(resp.status()).isEqualTo(EventStatus.PENDING);
        verify(inconsistencyRepo).save(any());
    }

    @Test
    void orderCancelled_stockAlreadyRestoredByMarketplace_noDoubleRestore() {
        when(stockEventRepo.existsByEventId(anyString())).thenReturn(false);
        Order order = Order.create(MARKETPLACE, ACCOUNT_ID, ORDER_ID, SKU, 2);
        order.setStockRestored(true); // marketplace already restored
        when(orderRepo.findByKey(MARKETPLACE, ACCOUNT_ID, ORDER_ID, SKU))
            .thenReturn(Optional.of(order));

        EventResponse resp = service.process(buildOrderCancelled("evt-002"));

        assertThat(resp.status()).isEqualTo(EventStatus.PROCESSED);
        verify(stockRepo, never()).save(argThat(s -> s.getAvailableQuantity() > 0));
    }

    // ─── MARKETPLACE_STOCK_RESTORED ───────────────────────────────────────────

    @Test
    void marketplaceStockRestored_restoresStock() {
        when(stockEventRepo.existsByEventId(anyString())).thenReturn(false);
        Order order = Order.create(MARKETPLACE, ACCOUNT_ID, ORDER_ID, SKU, 2);
        when(orderRepo.findByKey(MARKETPLACE, ACCOUNT_ID, ORDER_ID, SKU))
            .thenReturn(Optional.of(order));
        Stock stock = Stock.create(ACCOUNT_ID, SKU);
        stock.setAvailableQuantity(8);
        when(stockRepo.findByAccountIdAndSkuWithLock(ACCOUNT_ID, SKU)).thenReturn(Optional.of(stock));

        EventResponse resp = service.process(buildMarketplaceRestored("evt-005", 2));

        assertThat(resp.status()).isEqualTo(EventStatus.PROCESSED);
        verify(stockRepo).save(argThat(s -> s.getAvailableQuantity() == 10));
        verify(orderRepo).save(argThat(o -> o.getStockRestored()));
    }

    @Test
    void marketplaceStockRestored_alreadyRestored_returnsInconsistent() {
        when(stockEventRepo.existsByEventId(anyString())).thenReturn(false);
        Order order = Order.create(MARKETPLACE, ACCOUNT_ID, ORDER_ID, SKU, 2);
        order.setStockRestored(true);
        when(orderRepo.findByKey(MARKETPLACE, ACCOUNT_ID, ORDER_ID, SKU))
            .thenReturn(Optional.of(order));

        EventResponse resp = service.process(buildMarketplaceRestored("evt-006", 2));

        assertThat(resp.status()).isEqualTo(EventStatus.INCONSISTENT);
        verify(stockRepo, never()).save(any());
    }

    @Test
    void marketplaceStockRestored_noOrder_returnsInconsistent() {
        when(stockEventRepo.existsByEventId(anyString())).thenReturn(false);
        when(orderRepo.findByKey(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());

        EventResponse resp = service.process(buildMarketplaceRestored("evt-005", 2));

        assertThat(resp.status()).isEqualTo(EventStatus.INCONSISTENT);
    }

    // ─── Auto-resolve pending cancellation ───────────────────────────────────

    @Test
    void orderCreated_resolvesExistingPendingCancellation() {
        when(stockEventRepo.existsByEventId(anyString())).thenReturn(false);
        when(orderRepo.findByKey(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty())   // first call: no existing order
            .thenReturn(Optional.of(Order.create(MARKETPLACE, ACCOUNT_ID, ORDER_ID, SKU, 2))); // second call (for cancel)

        Stock stock = Stock.create(ACCOUNT_ID, SKU);
        stock.setAvailableQuantity(10);
        when(stockRepo.findByAccountIdAndSkuWithLock(ACCOUNT_ID, SKU)).thenReturn(Optional.of(stock));

        StockEvent pendingCancel = buildPendingCancelEvent();
        when(stockEventRepo.findPendingCancellations(MARKETPLACE, ACCOUNT_ID, ORDER_ID, SKU))
            .thenReturn(List.of(pendingCancel));

        service.process(buildOrderCreated("evt-001", 2));

        verify(stockEventRepo, atLeastOnce()).save(argThat(e ->
            e.getEventId().equals("evt-pending") && e.getStatus() == EventStatus.PROCESSED));
    }

    // ─── STOCK_SYNC_SENT ──────────────────────────────────────────────────────

    @Test
    void stockSyncSent_recordsHistory_noStockChange() {
        when(stockEventRepo.existsByEventId(anyString())).thenReturn(false);
        Stock stock = Stock.create(ACCOUNT_ID, SKU);
        stock.setAvailableQuantity(8);
        when(stockRepo.findByAccountIdAndSku(ACCOUNT_ID, SKU)).thenReturn(Optional.of(stock));

        EventResponse resp = service.process(buildSyncSent("evt-004", 8));

        assertThat(resp.status()).isEqualTo(EventStatus.PROCESSED);
        verify(stockRepo, never()).save(any()); // no stock modification
        verify(historyRepo).save(argThat(h -> h.getDelta() == 0));
    }

    // ─── builders ─────────────────────────────────────────────────────────────

    private EventRequest buildAdjust(String id, int available) {
        EventRequest r = new EventRequest();
        r.setEventId(id); r.setType(EventType.STOCK_ADJUSTED);
        r.setOccurredAt(LocalDateTime.now()); r.setAccountId(ACCOUNT_ID);
        r.setSku(SKU); r.setAvailable(available); r.setReason("manual");
        return r;
    }

    private EventRequest buildOrderCreated(String id, int qty) {
        EventRequest r = new EventRequest();
        r.setEventId(id); r.setType(EventType.ORDER_CREATED);
        r.setOccurredAt(LocalDateTime.now()); r.setMarketplace(MARKETPLACE);
        r.setAccountId(ACCOUNT_ID); r.setExternalOrderId(ORDER_ID);
        r.setSku(SKU); r.setQuantity(qty);
        return r;
    }

    private EventRequest buildOrderCancelled(String id) {
        EventRequest r = new EventRequest();
        r.setEventId(id); r.setType(EventType.ORDER_CANCELLED);
        r.setOccurredAt(LocalDateTime.now()); r.setMarketplace(MARKETPLACE);
        r.setAccountId(ACCOUNT_ID); r.setExternalOrderId(ORDER_ID);
        r.setSku(SKU); r.setQuantity(2);
        return r;
    }

    private EventRequest buildMarketplaceRestored(String id, int qty) {
        EventRequest r = new EventRequest();
        r.setEventId(id); r.setType(EventType.MARKETPLACE_STOCK_RESTORED);
        r.setOccurredAt(LocalDateTime.now()); r.setMarketplace(MARKETPLACE);
        r.setAccountId(ACCOUNT_ID); r.setExternalOrderId(ORDER_ID);
        r.setSku(SKU); r.setQuantity(qty);
        return r;
    }

    private EventRequest buildSyncSent(String id, int qty) {
        EventRequest r = new EventRequest();
        r.setEventId(id); r.setType(EventType.STOCK_SYNC_SENT);
        r.setOccurredAt(LocalDateTime.now()); r.setMarketplace(MARKETPLACE);
        r.setAccountId(ACCOUNT_ID); r.setSku(SKU); r.setQuantitySent(qty);
        return r;
    }

    private StockEvent buildPendingCancelEvent() {
        StockEvent e = new StockEvent();
        e.setEventId("evt-pending"); e.setType(EventType.ORDER_CANCELLED);
        e.setStatus(EventStatus.PENDING); e.setAccountId(ACCOUNT_ID);
        e.setSku(SKU); e.setMarketplace(MARKETPLACE);
        e.setExternalOrderId(ORDER_ID); e.setQuantity(2);
        e.setOccurredAt(LocalDateTime.now());
        return e;
    }
}
