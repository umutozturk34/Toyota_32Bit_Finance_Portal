package com.finance.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.finance.backend.util.PercentChangeCalculator;
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

    @Column(name = "yahoo_symbol", length = 20)
    private String yahooSymbol;

    @Column(name = "current_price", precision = 19, scale = 4)
    private BigDecimal currentPrice;

    @Column(name = "selling_price", precision = 19, scale = 4)
    private BigDecimal sellingPrice;

    @Column(name = "current_price_usd", precision = 19, scale = 4)
    private BigDecimal currentPriceUsd;

    @Column(name = "previous_price_usd", precision = 19, scale = 4)
    private BigDecimal previousPriceUsd;

    @Column(name = "change_24h", precision = 19, scale = 4)
    private BigDecimal change24h;

    @Column(name = "change_percent_24h", precision = 19, scale = 4)
    private BigDecimal changePercent24h;

    @Column(name = "unit")
    private String unit;

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

    public void applyYahooSnapshot(CommoditySnapshotInput snapshot, BigDecimal spreadRate, int scale) {
        if (snapshot == null || snapshot.tryPrice() == null) return;
        this.currentPrice = scaleValue(snapshot.tryPrice(), scale);
        this.sellingPrice = scaleValue(snapshot.tryPrice().multiply(BigDecimal.ONE.add(spreadRate)), scale);
        this.currentPriceUsd = scaleValue(snapshot.usdPrice(), scale);
        this.previousPriceUsd = scaleValue(snapshot.usdPreviousClose(), scale);
        applyChangeFields(snapshot.tryPrice(), snapshot.tryPreviousClose(), scale);
        this.yahooUpdatedAt = LocalDateTime.now();
    }

    public void applyDerivedSnapshot(BigDecimal tryPrice, BigDecimal tryPreviousClose,
                                     BigDecimal spreadRate, int scale) {
        if (tryPrice == null) return;
        this.currentPrice = scaleValue(tryPrice, scale);
        this.sellingPrice = scaleValue(tryPrice.multiply(BigDecimal.ONE.add(spreadRate)), scale);
        applyChangeFields(tryPrice, tryPreviousClose, scale);
        this.yahooUpdatedAt = LocalDateTime.now();
    }

    private void applyChangeFields(BigDecimal current, BigDecimal previous, int scale) {
        PercentChangeCalculator.Result result = PercentChangeCalculator.compute(current, previous, scale);
        this.change24h = result.amount();
        this.changePercent24h = result.percent();
    }

    @Override
    public String getCode() {
        return commodityCode;
    }

    @Override
    public String resolveDisplayName() {
        return firstNonBlank(getName(), commodityNameTr, commodityName, commodityCode);
    }
}
