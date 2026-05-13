package com.finance.market.commodity.model;

import com.finance.market.core.model.BaseAsset;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "commodities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Commodity extends BaseAsset {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "commodity_code", length = 30)
    private String commodityCode;

    @Column(name = "commodity_name")
    private String commodityName;

    @Column(name = "commodity_name_tr")
    private String commodityNameTr;

    @Column(name = "current_price", precision = 19, scale = 4)
    private BigDecimal currentPrice;

    @Column(name = "current_price_usd", precision = 19, scale = 4)
    private BigDecimal currentPriceUsd;

    @Column(name = "previous_price_usd", precision = 19, scale = 4)
    private BigDecimal previousPriceUsd;

    @Column(name = "unit")
    private String unit;

    @Column(name = "commodity_segment", length = 20)
    @Enumerated(EnumType.STRING)
    private CommoditySegment commoditySegment;

    @Column(name = "yahoo_symbol", length = 20)
    private String yahooSymbol;

    @Column(name = "open_price", precision = 19, scale = 4)
    private BigDecimal openPrice;

    @Column(name = "day_high", precision = 19, scale = 4)
    private BigDecimal dayHigh;

    @Column(name = "day_low", precision = 19, scale = 4)
    private BigDecimal dayLow;

    @Column(name = "volume")
    private Long volume;

    @Column(name = "yahoo_updated_at")
    private LocalDateTime yahooUpdatedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "commodity", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<CommodityCandle> candles;

    public void applyPriceSnapshot(CommoditySnapshotInput snapshot, int scale) {
        if (snapshot == null || snapshot.tryPrice() == null) return;
        this.currentPrice = scaleValue(snapshot.tryPrice(), scale);
        this.currentPriceUsd = scaleValue(snapshot.usdPrice(), scale);
        this.previousPriceUsd = scaleValue(snapshot.usdPreviousClose(), scale);
        this.openPrice = scaleValue(snapshot.tryOpenPrice(), scale);
        this.dayHigh = scaleValue(snapshot.tryDayHigh(), scale);
        this.dayLow = scaleValue(snapshot.tryDayLow(), scale);
        this.volume = snapshot.volume();
        applyChangePreferring(snapshot.tryPrice(), snapshot.tryPreviousClose(),
                null, snapshot.yahooChangePercent(), scale);
        this.yahooUpdatedAt = LocalDateTime.now();
    }

    @Override
    public void scaleFields(int scale) {
        this.currentPrice = scaleValue(this.currentPrice, scale);
        this.currentPriceUsd = scaleValue(this.currentPriceUsd, scale);
        this.previousPriceUsd = scaleValue(this.previousPriceUsd, scale);
        this.openPrice = scaleValue(this.openPrice, scale);
        this.dayHigh = scaleValue(this.dayHigh, scale);
        this.dayLow = scaleValue(this.dayLow, scale);
    }

    @Override
    public String getCode() {
        return commodityCode;
    }

    @Override
    public BigDecimal getPriceTry() {
        return getCurrentPrice();
    }

    @Override
    public String resolveDisplayName() {
        return firstNonBlank(getName(), commodityNameTr, commodityName, commodityCode);
    }
}
