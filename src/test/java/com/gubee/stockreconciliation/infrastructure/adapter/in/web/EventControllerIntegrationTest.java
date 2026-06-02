package com.gubee.stockreconciliation.infrastructure.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gubee.stockreconciliation.domain.model.EventStatus;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("gubee_stock_test")
        .withUsername("gubee")
        .withPassword("gubee");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // ─── Scenario 1: Initial stock adjustment ─────────────────────────────────

    @Test
    @Order(1)
    void scenario1_stockAdjusted_setsStockTo10() throws Exception {
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"eventId":"sc1-evt-001","type":"STOCK_ADJUSTED",
                     "occurredAt":"2026-05-28T10:00:00Z",
                     "accountId":"sc1-account","sku":"SC1-SKU",
                     "available":10,"reason":"initial"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PROCESSED"));

        mockMvc.perform(get("/stocks/sc1-account/SC1-SKU"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableQuantity").value(10));
    }

    // ─── Scenario 2: Order deducts stock ─────────────────────────────────────

    @Test
    @Order(2)
    void scenario2_orderCreated_deductsStockTo8() throws Exception {
        // Set stock to 10
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"eventId":"sc2-adj","type":"STOCK_ADJUSTED",
                     "occurredAt":"2026-05-28T10:00:00Z",
                     "accountId":"sc2-account","sku":"SC2-SKU","available":10}
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"eventId":"sc2-order","type":"ORDER_CREATED",
                     "occurredAt":"2026-05-28T10:01:00Z",
                     "marketplace":"ML","accountId":"sc2-account",
                     "externalOrderId":"ORD-002","sku":"SC2-SKU","quantity":2}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PROCESSED"));

        mockMvc.perform(get("/stocks/sc2-account/SC2-SKU"))
            .andExpect(jsonPath("$.availableQuantity").value(8));
    }

    // ─── Scenario 3: Cancellation restores stock ──────────────────────────────

    @Test
    @Order(3)
    void scenario3_orderCancelled_restoresStock() throws Exception {
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"eventId":"sc3-adj","type":"STOCK_ADJUSTED",
                     "occurredAt":"2026-05-28T10:00:00Z",
                     "accountId":"sc3-account","sku":"SC3-SKU","available":10}
                    """)).andExpect(status().isOk());

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"eventId":"sc3-create","type":"ORDER_CREATED",
                     "occurredAt":"2026-05-28T10:01:00Z",
                     "marketplace":"ML","accountId":"sc3-account",
                     "externalOrderId":"ORD-003","sku":"SC3-SKU","quantity":2}
                    """)).andExpect(status().isOk());

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"eventId":"sc3-cancel","type":"ORDER_CANCELLED",
                     "occurredAt":"2026-05-28T10:02:00Z",
                     "marketplace":"ML","accountId":"sc3-account",
                     "externalOrderId":"ORD-003","sku":"SC3-SKU","quantity":2}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PROCESSED"));

        mockMvc.perform(get("/stocks/sc3-account/SC3-SKU"))
            .andExpect(jsonPath("$.availableQuantity").value(10));
    }

    // ─── Scenario 4: Duplicate eventId ────────────────────────────────────────

    @Test
    @Order(4)
    void scenario4_duplicateEventId_returnsIgnored() throws Exception {
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"eventId":"sc4-evt","type":"STOCK_ADJUSTED",
                     "occurredAt":"2026-05-28T10:00:00Z",
                     "accountId":"sc4-account","sku":"SC4-SKU","available":10}
                    """)).andExpect(status().isOk());

        // Same eventId again
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"eventId":"sc4-evt","type":"STOCK_ADJUSTED",
                     "occurredAt":"2026-05-28T10:00:00Z",
                     "accountId":"sc4-account","sku":"SC4-SKU","available":999}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IGNORED"));

        // Stock must remain 10, not 999
        mockMvc.perform(get("/stocks/sc4-account/SC4-SKU"))
            .andExpect(jsonPath("$.availableQuantity").value(10));
    }

    // ─── Scenario 5: Duplicate cancellation ───────────────────────────────────

    @Test
    @Order(5)
    void scenario5_duplicateCancellation_returnsInconsistent() throws Exception {
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"eventId":"sc5-adj","type":"STOCK_ADJUSTED",
                     "occurredAt":"2026-05-28T10:00:00Z",
                     "accountId":"sc5-account","sku":"SC5-SKU","available":10}
                    """)).andExpect(status().isOk());

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"eventId":"sc5-create","type":"ORDER_CREATED",
                     "occurredAt":"2026-05-28T10:01:00Z",
                     "marketplace":"ML","accountId":"sc5-account",
                     "externalOrderId":"ORD-005","sku":"SC5-SKU","quantity":2}
                    """)).andExpect(status().isOk());

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"eventId":"sc5-cancel-1","type":"ORDER_CANCELLED",
                     "occurredAt":"2026-05-28T10:02:00Z",
                     "marketplace":"ML","accountId":"sc5-account",
                     "externalOrderId":"ORD-005","sku":"SC5-SKU","quantity":2}
                    """)).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PROCESSED"));

        // Second cancellation (different eventId, same order)
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"eventId":"sc5-cancel-2","type":"ORDER_CANCELLED",
                     "occurredAt":"2026-05-28T10:03:00Z",
                     "marketplace":"ML","accountId":"sc5-account",
                     "externalOrderId":"ORD-005","sku":"SC5-SKU","quantity":2}
                    """))
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.status").value("INCONSISTENT"));

        // Stock should be 10 (restored once, not twice)
        mockMvc.perform(get("/stocks/sc5-account/SC5-SKU"))
            .andExpect(jsonPath("$.availableQuantity").value(10));
    }

    // ─── Scenario 6: Cancel before create ────────────────────────────────────

    @Test
    @Order(6)
    void scenario6_cancelBeforeCreate_returnsPending() throws Exception {
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"eventId":"sc6-cancel","type":"ORDER_CANCELLED",
                     "occurredAt":"2026-05-28T10:05:00Z",
                     "marketplace":"ML","accountId":"sc6-account",
                     "externalOrderId":"ORD-006","sku":"SC6-SKU","quantity":2}
                    """))
            .andExpect(status().is(202))
            .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/events?status=PENDING"))
            .andExpect(jsonPath("$[?(@.eventId == 'sc6-cancel')]").exists());
    }

    // ─── Scenario 8: Marketplace restore + cancel no duplication ─────────────

    @Test
    @Order(8)
    void scenario8_marketplaceRestoreFollowedByCancel_noDoublerestore() throws Exception {
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"eventId":"sc8-adj","type":"STOCK_ADJUSTED",
                     "occurredAt":"2026-05-28T10:00:00Z",
                     "accountId":"sc8-account","sku":"SC8-SKU","available":10}
                    """)).andExpect(status().isOk());

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"eventId":"sc8-create","type":"ORDER_CREATED",
                     "occurredAt":"2026-05-28T10:01:00Z",
                     "marketplace":"ML","accountId":"sc8-account",
                     "externalOrderId":"ORD-008","sku":"SC8-SKU","quantity":2}
                    """)).andExpect(status().isOk());

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"eventId":"sc8-restore","type":"MARKETPLACE_STOCK_RESTORED",
                     "occurredAt":"2026-05-28T10:20:00Z",
                     "marketplace":"ML","accountId":"sc8-account",
                     "externalOrderId":"ORD-008","sku":"SC8-SKU","quantity":2}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PROCESSED"));

        // Stock should now be 10
        mockMvc.perform(get("/stocks/sc8-account/SC8-SKU"))
            .andExpect(jsonPath("$.availableQuantity").value(10));

        // ORDER_CANCELLED arrives after marketplace already restored — should not double-restore
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"eventId":"sc8-cancel","type":"ORDER_CANCELLED",
                     "occurredAt":"2026-05-28T10:25:00Z",
                     "marketplace":"ML","accountId":"sc8-account",
                     "externalOrderId":"ORD-008","sku":"SC8-SKU","quantity":2}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PROCESSED"));

        // Stock should still be 10, not 12
        mockMvc.perform(get("/stocks/sc8-account/SC8-SKU"))
            .andExpect(jsonPath("$.availableQuantity").value(10));
    }

    // ─── History endpoint ─────────────────────────────────────────────────────

    @Test
    @Order(9)
    void historyEndpoint_returnsAuditTrail() throws Exception {
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"eventId":"hist-adj","type":"STOCK_ADJUSTED",
                     "occurredAt":"2026-05-28T10:00:00Z",
                     "accountId":"hist-acc","sku":"HIST-SKU","available":5}
                    """)).andExpect(status().isOk());

        mockMvc.perform(get("/stocks/hist-acc/HIST-SKU/history"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$[0].eventType").value("STOCK_ADJUSTED"));
    }
}
