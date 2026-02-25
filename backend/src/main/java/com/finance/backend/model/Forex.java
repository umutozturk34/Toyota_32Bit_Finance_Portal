package com.finance.backend.model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "forex")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Forex {
    @Id
    @EqualsAndHashCode.Include
    @Column(name = "currency_code", length = 10)
    private String currencyCode;
    @Column(name = "currency_name")
    private String currencyName;
    @Column(name = "currency_name_tr")
    private String currencyNameTr;
    @Column(name = "unit")
    private Integer unit;
    @Column(name = "forex_buying", precision = 19, scale = 4)
    private BigDecimal forexBuying;
    @Column(name = "forex_selling", precision = 19, scale = 4)
    private BigDecimal forexSelling;
    @Column(name = "banknote_buying", precision = 19, scale = 4)
    private BigDecimal banknoteBuying;
    @Column(name = "banknote_selling", precision = 19, scale = 4)
    private BigDecimal banknoteSelling;
    @Column(name = "current_price", precision = 19, scale = 4)
    private BigDecimal currentPrice;
    @Column(name = "selling_price", precision = 19, scale = 4)
    private BigDecimal sellingPrice;
    @Column(name = "change_24h", precision = 19, scale = 4)
    private BigDecimal change24h;
    @Column(name = "change_percent_24h", precision = 19, scale = 4)
    private BigDecimal changePercent24h;
    @Column(name = "cross_rate_usd", precision = 19, scale = 4)
    private BigDecimal crossRateUsd;
    @Column(name = "cross_rate_other", precision = 19, scale = 4)
    private BigDecimal crossRateOther;
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    @Column(name = "tcmb_updated_at")
    private LocalDateTime tcmbUpdatedAt;
    @Column(name = "yahoo_updated_at")
    private LocalDateTime yahooUpdatedAt;
    @OneToMany(mappedBy = "forex", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<ForexCandle> candles;
}