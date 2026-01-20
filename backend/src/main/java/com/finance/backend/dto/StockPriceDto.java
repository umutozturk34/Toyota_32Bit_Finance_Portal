package com.finance.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockPriceDto implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String symbol;
    private String name;
    private BigDecimal price;
    private BigDecimal changeAmount;
    private BigDecimal changePercent;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private Long volume;
    private String currency;
    private String exchange;
    private LocalDateTime timestamp;
}
