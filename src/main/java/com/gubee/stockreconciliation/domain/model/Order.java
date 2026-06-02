package com.gubee.stockreconciliation.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "orders", uniqueConstraints = {
    @UniqueConstraint(name = "uk_orders_key", columnNames = {"marketplace", "account_id", "external_order_id", "sku"})
})
@Getter @Setter @NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "marketplace", nullable = false)
    private String marketplace;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "external_order_id", nullable = false)
    private String externalOrderId;

    @Column(name = "sku", nullable = false)
    private String sku;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    /** True when stock was restored (by ORDER_CANCELLED or MARKETPLACE_STOCK_RESTORED). */
    @Column(name = "stock_restored", nullable = false)
    private Boolean stockRestored = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static Order create(String marketplace, String accountId, String externalOrderId,
                                String sku, Integer quantity) {
        Order o = new Order();
        o.marketplace = marketplace;
        o.accountId = accountId;
        o.externalOrderId = externalOrderId;
        o.sku = sku;
        o.quantity = quantity;
        o.status = OrderStatus.CREATED;
        o.stockRestored = false;
        o.createdAt = LocalDateTime.now();
        return o;
    }
}
