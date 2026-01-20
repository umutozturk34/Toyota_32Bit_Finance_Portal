package com.finance.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "crypto_prices")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CryptoPrice {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String symbol;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal priceUsd;
    
    @Column(precision = 19, scale = 4)
    private BigDecimal priceTry;
    
    @Column(precision = 10, scale = 4)
    private BigDecimal changePercent24h;
    
    @Column(precision = 19, scale = 2)
    private BigDecimal marketCapUsd;
    
    @Column(precision = 19, scale = 2)
    private BigDecimal volume24hUsd;
    
    private Integer marketCapRank;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
