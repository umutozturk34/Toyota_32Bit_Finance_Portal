package com.finance.backend.model;

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
    
    /**
     * Candle date/timestamp
     */
    private LocalDateTime candleDate;
    
    /**
     * Opening price
     */
    private BigDecimal open;
    
    /**
     * Highest price
     */
    private BigDecimal high;
    
    /**
     * Lowest price
     */
    private BigDecimal low;
    
    /**
     * Closing price
     */
    private BigDecimal close;
}
