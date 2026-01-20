package com.finance.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_prices")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockPrice {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String symbol;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false)
    private String market;
    
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;
    
    @Column(precision = 19, scale = 4)
    private BigDecimal changeAmount;
    
    @Column(precision = 10, scale = 4)
    private BigDecimal changePercent;
    
    @Column(precision = 19, scale = 4)
    private BigDecimal open;
    
    @Column(precision = 19, scale = 4)
    private BigDecimal high;
    
    @Column(precision = 19, scale = 4)
    private BigDecimal low;
    
    private Long volume;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
