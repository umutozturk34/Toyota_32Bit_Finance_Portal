package com.finance.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "metal_prices")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetalPrice implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String symbol; // GOLD, SILVER, PLATINUM
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal priceUsd;
    
    @Column(precision = 19, scale = 8)
    private BigDecimal changeAmount;
    
    @Column(precision = 10, scale = 6)
    private BigDecimal changePercent;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}