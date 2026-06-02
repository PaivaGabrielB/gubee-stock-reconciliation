package com.gubee.stockreconciliation.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.gubee.stockreconciliation.domain.model.EventType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Incoming stock/order event")
public class EventRequest {

    @NotBlank
    @Schema(example = "evt-001")
    private String eventId;

    @NotNull
    @Schema(example = "ORDER_CREATED")
    private EventType type;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    @Schema(example = "2026-05-28T10:00:00Z")
    private LocalDateTime occurredAt;

    @Schema(example = "MERCADO_LIVRE")
    private String marketplace;

    @NotBlank
    @Schema(example = "account-001")
    private String accountId;

    @Schema(example = "ML-123456")
    private String externalOrderId;

    @NotBlank
    @Schema(example = "ABC-123")
    private String sku;

    @Schema(description = "Used by ORDER_CREATED, ORDER_CANCELLED, MARKETPLACE_STOCK_RESTORED", example = "2")
    private Integer quantity;

    @Schema(description = "Used by STOCK_ADJUSTED — absolute quantity", example = "10")
    private Integer available;

    @Schema(description = "Used by STOCK_ADJUSTED", example = "manual_adjustment")
    private String reason;

    @Schema(description = "Used by STOCK_SYNC_SENT", example = "8")
    private Integer quantitySent;
}
