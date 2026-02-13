package com.finance.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "stock_candles",
    uniqueConstraints = @UniqueConstraint(columnNames = {"stock_symbol", "candle_date"}),
    indexes = {
        @Index(name = "idx_stock_candle_symbol", columnList = "stock_symbol"),
        @Index(name = "idx_stock_candle_date", columnList = "candle_date"),
        @Index(name = "idx_stock_candle_symbol_date", columnList = "stock_symbol, candle_date")
    }
)
public class StockCandle extends BaseCandle {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_symbol", nullable = false)
    private String stockSymbol;
}
