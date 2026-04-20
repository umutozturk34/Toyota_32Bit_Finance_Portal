package com.finance.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "portfolio_transactions",
        indexes = {
                @Index(name = "idx_portfolio_transactions_portfolio", columnList = "portfolio_id"),
                @Index(name = "idx_portfolio_transactions_pf_created",
                        columnList = "portfolio_id, created_at")
        })
public class PortfolioTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(name = "portfolio_id", insertable = false, updatable = false)
    private Long portfolioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 50)
    private AssetType assetType;

    @Column(name = "asset_code", nullable = false, length = 100)
    private String assetCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 10)
    private TransactionSide side;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(name = "unit_price_try", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPriceTry;

    @Column(name = "total_cost_try", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCostTry;

    @Column(name = "fee_try", nullable = false, precision = 19, scale = 4)
    private BigDecimal feeTry;

    @Column(name = "realized_pnl_try", precision = 19, scale = 4)
    private BigDecimal realizedPnlTry;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
