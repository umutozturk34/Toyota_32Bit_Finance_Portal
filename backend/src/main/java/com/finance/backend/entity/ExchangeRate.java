package com.finance.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "exchange_rates", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"currency_code", "rate_date"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRate {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 3)
    private String currencyCode;
    
    @Column(nullable = false)
    private String currencyName;
    
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal buyingRate;
    
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal sellingRate;
    
    @Column(nullable = false)
    private LocalDate rateDate;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
