package com.finance.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "forex_candles", 
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"currency_code", "candle_date"})
    },
    indexes = {
        @Index(name = "idx_forex_candle_currency", columnList = "currency_code"),
        @Index(name = "idx_forex_candle_date", columnList = "candle_date")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "forex")
@EqualsAndHashCode(exclude = "forex")
public class ForexCandle {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "currency_code", nullable = false, length = 10, insertable = false, updatable = false)
    private String currencyCode;  // USD, EUR, GBP, etc.
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_code", referencedColumnName = "currency_code", nullable = false)
    @JsonIgnore
    private Forex forex;
    
    @Column(name = "candle_date", nullable = false)
    private LocalDateTime candleDate;
    
    // OHLC Data (Yahoo Finance'den)
    @Column(name = "open", precision = 18, scale = 4)
    private BigDecimal open;
    
    @Column(name = "high", precision = 18, scale = 4)
    private BigDecimal high;
    
    @Column(name = "low", precision = 18, scale = 4)
    private BigDecimal low;
    
    @Column(name = "close", precision = 18, scale = 4)
    private BigDecimal close;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
