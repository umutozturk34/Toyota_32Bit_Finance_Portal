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

/**
 * A hypothetical VIOP derivative lot (future/option) on a {@link ViopContract}, taken {@code LONG}
 * or {@code SHORT} for {@code quantityLot} lots from {@code entryPrice} on {@code entryDate}.
 * <p>{@code entryPrice}/{@code closePrice} are stored already converted to TRY at their respective
 * dates' FX rates. PnL is delegated to {@link DerivativeDirection#pnlPerLot} scaled by the contract
 * size and lot count. A position is open until {@code closeDate} is set; closing also records why
 * via {@link DerivativeCloseReason}.
 */
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

    /** Margin tied up in TRY (initial margin × lots); null when contract margin is unknown. */
    public BigDecimal lockedMargin() {
        if (viopContract == null || viopContract.getInitialMargin() == null || quantityLot == null) return null;
        return viopContract.getInitialMargin().multiply(quantityLot);
    }

    /** Notional exposure in TRY at entry (entry price × contract size × lots); null when inputs are missing. */
    public BigDecimal nominalExposure() {
        return notionalAt(entryPrice);
    }

    /**
     * Absolute (direction-blind) notional value at a given price: price × contract size × lots. This is the
     * market-value basis used everywhere (open rowMv, the positions grid), so a SHORT's value/exit folds in
     * as {@code close × size × lots}, not the directional {@code entryNotional + realized} which diverges.
     * Null when inputs are missing.
     */
    public BigDecimal notionalAt(BigDecimal price) {
        if (viopContract == null || price == null || quantityLot == null) return null;
        BigDecimal size = viopContract.getContractSize() != null
                ? viopContract.getContractSize() : BigDecimal.ONE;
        return price.multiply(size).multiply(quantityLot);
    }

    /**
     * PnL in TRY: realized against {@code closePrice} when closed, otherwise unrealized against
     * {@code currentPrice}. Sign follows {@link DerivativeDirection}; null when no exit price is available.
     */
    public BigDecimal realizedOrUnrealizedPnl(BigDecimal currentPrice) {
        BigDecimal exit = closePrice != null ? closePrice : currentPrice;
        if (exit == null || viopContract == null) return null;
        BigDecimal perLot = direction.pnlPerLot(entryPrice, exit, viopContract.getContractSize());
        return perLot != null ? perLot.multiply(quantityLot) : null;
    }

    /**
     * Closes the position; {@code price} must already be in TRY (converted at the close date's FX rate).
     *
     * @throws IllegalStateException if already closed
     */
    public void closeWith(LocalDate date, BigDecimal price, DerivativeCloseReason reason) {
        if (!isOpen()) {
            throw new IllegalStateException("Position " + id + " is already closed");
        }
        this.closeDate = date;
        this.closePrice = price;
        this.closeReason = reason;
    }

    /** Convenience close with reason {@link DerivativeCloseReason#USER_CLOSED}. */
    public void closeFull(LocalDate date, BigDecimal price) {
        closeWith(date, price, DerivativeCloseReason.USER_CLOSED);
    }

    /** Clears close fields so an edit/reopen flow can re-derive them; unlike {@link #closeWith} this bypasses the open guard. */
    public void reopenForUpdate() {
        this.closeDate = null;
        this.closePrice = null;
        this.closeReason = null;
    }

    /**
     * Shrinks the remaining lot count for a partial close.
     *
     * @throws IllegalArgumentException if {@code soldQty} is non-positive
     * @throws IllegalStateException if {@code soldQty} is not strictly less than the remaining lots
     */
    public void reduceQuantity(BigDecimal soldQty) {
        if (soldQty == null || soldQty.signum() <= 0) {
            throw new IllegalArgumentException("soldQty must be positive");
        }
        if (quantityLot == null || soldQty.compareTo(quantityLot) >= 0) {
            throw new IllegalStateException("soldQty exceeds quantityLot");
        }
        this.quantityLot = quantityLot.subtract(soldQty);
    }

    /** Replaces entry attributes wholesale (direction/date/price/lots); {@code entryPrice} expected in TRY. */
    public void updateEntry(DerivativeDirection direction, LocalDate entryDate,
                             BigDecimal entryPrice, BigDecimal quantityLot) {
        this.direction = direction;
        this.entryDate = entryDate;
        this.entryPrice = entryPrice;
        this.quantityLot = quantityLot;
    }
}
