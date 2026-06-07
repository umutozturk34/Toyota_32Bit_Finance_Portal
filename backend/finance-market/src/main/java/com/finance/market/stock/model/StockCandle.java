package com.finance.market.stock.model;

import com.finance.market.core.model.BaseCandle;
import com.finance.market.stock.model.Stock;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import java.util.Objects;
/**
 * Daily OHLC candle for a stock, unique per (symbol, date). The {@code stockSymbol} column is
 * read-only; the {@code stock} association owns the foreign key.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "stock_candles",
    uniqueConstraints = @UniqueConstraint(
        name = "uc_stock_symbol_date",
        columnNames = {"stock_symbol", "candle_date"}
    ),
    indexes = {
        @Index(name = "idx_stock_candles_symbol", columnList = "stock_symbol"),
        @Index(name = "idx_stock_candles_date", columnList = "candle_date"),
        @Index(name = "idx_stock_candles_symbol_date", columnList = "stock_symbol, candle_date")
    }
)
public class StockCandle extends BaseCandle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_symbol", referencedColumnName = "symbol", nullable = false,
            foreignKey = @ForeignKey(name = "fk_stock_candle_symbol"))
    @JsonIgnore
    private Stock stock;
    @Column(name = "stock_symbol", insertable = false, updatable = false, nullable = false)
    private String stockSymbol;

    @Column(name = "volume")
    private Long volume;
    /**
     * Identity equality based solely on the persisted {@code id}. A candle with a null id (not yet
     * persisted) is never equal to another instance, which keeps transient entities distinct.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StockCandle that)) return false;
        return id != null && Objects.equals(id, that.id);
    }
    /**
     * Class-constant hash so an entity's bucket stays stable across the transient-to-persistent
     * transition (before and after an id is assigned), as recommended for JPA entities.
     */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}