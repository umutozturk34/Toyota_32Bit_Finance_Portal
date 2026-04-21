package com.finance.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    public void applyYahooSnapshot(BigDecimal marketPrice, BigDecimal previousClose,
                                   BigDecimal spreadRate, int scale) {
        if (marketPrice == null) return;
        this.currentPrice = scaleValue(marketPrice, scale);
        this.sellingPrice = scaleValue(marketPrice.multiply(BigDecimal.ONE.add(spreadRate)), scale);
        applyChangeFields(marketPrice, previousClose, scale);
        this.yahooUpdatedAt = LocalDateTime.now();
    }

    public void applySyntheticPrice(BigDecimal syntheticPrice, BigDecimal syntheticPreviousClose,
                                    BigDecimal spreadRate, int scale) {
        if (syntheticPrice == null) return;
        this.currentPrice = scaleValue(syntheticPrice, scale);
        this.sellingPrice = scaleValue(syntheticPrice.multiply(BigDecimal.ONE.add(spreadRate)), scale);
        if (syntheticPreviousClose != null) {
            applyChangeFields(syntheticPrice, syntheticPreviousClose, scale);
        }
        this.yahooUpdatedAt = LocalDateTime.now();
    }

    private void applyChangeFields(BigDecimal current, BigDecimal previous, int scale) {
        if (previous == null || previous.signum() == 0) {
            this.change24h = null;
            this.changePercent24h = null;
            return;
        }
        BigDecimal change = current.subtract(previous);
        this.change24h = scaleValue(change, scale);
        this.changePercent24h = change.divide(previous, scale + 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(scale, RoundingMode.HALF_UP);
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
