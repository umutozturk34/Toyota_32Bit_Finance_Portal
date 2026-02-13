package com.finance.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "forex", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"currency_code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "candles")
@EqualsAndHashCode(exclude = "candles")
public class Forex {
    
    @Id
    @Column(name = "currency_code", length = 10)
    private String currencyCode;
    
    @Column(name = "currency_name")
    private String currencyName;
    
    @Column(name = "currency_name_tr")
    private String currencyNameTr;
    
    @Column(name = "unit")
    private Integer unit;
    
    @Column(name = "forex_buying", precision = 18, scale = 4)
    private BigDecimal forexBuying;
    
    @Column(name = "forex_selling", precision = 18, scale = 4)
    private BigDecimal forexSelling;
    
    @Column(name = "banknote_buying", precision = 18, scale = 4)
    private BigDecimal banknoteBuying; 
    
    @Column(name = "banknote_selling", precision = 18, scale = 4)
    private BigDecimal banknoteSelling; 
    
    @Column(name = "current_price", precision = 18, scale = 4)
    private BigDecimal currentPrice;
    
    @Column(name = "selling_price", precision = 18, scale = 4)
    private BigDecimal sellingPrice;
    
    @Column(name = "change_24h", precision = 18, scale = 4)
    private BigDecimal change24h;
    
    @Column(name = "change_percent_24h", precision = 18, scale = 4)
    private BigDecimal changePercent24h;
    
    @Column(name = "cross_rate_usd", precision = 18, scale = 4)
    private BigDecimal crossRateUsd;
    
    @Column(name = "cross_rate_other", precision = 18, scale = 4)
    private BigDecimal crossRateOther;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "tcmb_updated_at")
    private LocalDateTime tcmbUpdatedAt;
    
    @Column(name = "yahoo_updated_at")
    private LocalDateTime yahooUpdatedAt;
    
    @OneToMany(mappedBy = "forex", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore 
    private List<ForexCandle> candles;
    
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
