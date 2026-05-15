package com.finance.portfolio.derivative.model;

import com.finance.market.viop.model.ViopContract;
import com.finance.portfolio.model.Portfolio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "portfolio_derivative_positions",
        indexes = {
                @Index(name = "idx_pdp_portfolio", columnList = "portfolio_id"),
                @Index(name = "idx_pdp_contract", columnList = "viop_contract_id"),
                @Index(name = "idx_pdp_open", columnList = "portfolio_id,close_date")
        })
public class DerivativePosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_pdp_portfolio"))
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "viop_contract_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_pdp_viop_contract"))
    private ViopContract viopContract;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 8)
    private DerivativeDirection direction;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "entry_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "quantity_lot", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityLot;

    @Setter
    @Column(name = "close_date")
    private LocalDate closeDate;

    @Setter
    @Column(name = "close_price", precision = 19, scale = 4)
    private BigDecimal closePrice;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "close_reason", length = 16)
    private DerivativeCloseReason closeReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isOpen() {
        return closeDate == null;
    }

    public BigDecimal lockedMargin() {
        if (viopContract == null || viopContract.getInitialMargin() == null || quantityLot == null) return null;
        return viopContract.getInitialMargin().multiply(quantityLot);
    }

    public BigDecimal nominalExposure() {
        if (viopContract == null || entryPrice == null || quantityLot == null) return null;
        BigDecimal size = viopContract.getContractSize() != null
                ? viopContract.getContractSize() : BigDecimal.ONE;
        return entryPrice.multiply(size).multiply(quantityLot);
    }

    public BigDecimal realizedOrUnrealizedPnl(BigDecimal currentPrice) {
        BigDecimal exit = closePrice != null ? closePrice : currentPrice;
        if (exit == null || viopContract == null) return null;
        BigDecimal perLot = direction.pnlPerLot(entryPrice, exit, viopContract.getContractSize());
        return perLot != null ? perLot.multiply(quantityLot) : null;
    }

    public void closeWith(LocalDate date, BigDecimal price, DerivativeCloseReason reason) {
        if (!isOpen()) {
            throw new IllegalStateException("Position " + id + " is already closed");
        }
        this.closeDate = date;
        this.closePrice = price;
        this.closeReason = reason;
    }

    public void reopenForUpdate() {
        this.closeDate = null;
        this.closePrice = null;
        this.closeReason = null;
    }

    public void updateEntry(DerivativeDirection direction, LocalDate entryDate,
                             BigDecimal entryPrice, BigDecimal quantityLot) {
        this.direction = direction;
        this.entryDate = entryDate;
        this.entryPrice = entryPrice;
        this.quantityLot = quantityLot;
    }
}
