package com.finance.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "stocks",
    indexes = {
        @Index(name = "idx_stock_symbol", columnList = "symbol"),
        @Index(name = "idx_stock_exchange", columnList = "exchange")
    }
)
public class Stock extends BaseAsset {
    
    @Id
    private String symbol;
    
    private BigDecimal currentPrice;

    private BigDecimal previousClose;
    
    private BigDecimal openPrice;
    
    private BigDecimal dayHigh;
    
    private BigDecimal dayLow;
    
    private Long volume;
    
    private BigDecimal priceChangePercent;
    
    private BigDecimal priceChangeAmount;
    
    private String exchange;
    
    private String currency;
}
