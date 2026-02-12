package com.finance.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Base class for all candle/OHLC chart data entities
 * Contains OHLC (Open, High, Low, Close) price data
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@MappedSuperclass
public abstract class BaseCandle {
    
    @Column(name = "candle_date", nullable = false)
    private LocalDateTime candleDate;
    
    @Column(name = "open", nullable = false)
    private BigDecimal open;
    
    @Column(name = "high", nullable = false)
    private BigDecimal high;
    
    @Column(name = "low", nullable = false)
    private BigDecimal low;
    
    @Column(name = "close", nullable = false)
    private BigDecimal close;
    
    @Column(name = "volume", nullable = true)
    private Long volume;
}
