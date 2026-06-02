package com.gubee.stockreconciliation.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stocks", uniqueConstraints = {
    @UniqueConstraint(name = "uk_stocks_account_sku", columnNames = {"account_id", "sku"})
})
@Getter @Setter @NoArgsConstructor
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "sku", nullable = false)
    private String sku;

    @Column(name = "available_quantity", nullable = false)
    private Integer availableQuantity;

    @Column(name = "last_updated_at", nullable = false)
    private LocalDateTime lastUpdatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    public static Stock create(String accountId, String sku) {
        Stock s = new Stock();
        s.accountId = accountId;
        s.sku = sku;
        s.availableQuantity = 0;
        s.lastUpdatedAt = LocalDateTime.now();
        return s;
    }
}
