package com.finance.market.forex.model;

import com.finance.market.core.model.BaseAsset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "forex")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Forex extends BaseAsset {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "currency_code", length = 10)
    private String currencyCode;

    @Column(name = "buying_price", precision = 19, scale = 4)
    private BigDecimal buyingPrice;

    @Column(name = "selling_price", precision = 19, scale = 4)
    private BigDecimal sellingPrice;

    @Column(name = "effective_buying_price", precision = 19, scale = 4)
    private BigDecimal effectiveBuyingPrice;

    @Column(name = "effective_selling_price", precision = 19, scale = 4)
    private BigDecimal effectiveSellingPrice;

    public void applyEvdsSnapshot(LocalDateTime dataTimestamp,
                                  BigDecimal rawBuying, BigDecimal rawSelling,
                                  BigDecimal rawEffectiveBuying, BigDecimal rawEffectiveSelling,
                                  int unit, int scale) {
        BigDecimal divisor = unit <= 1 ? BigDecimal.ONE : BigDecimal.valueOf(unit);
        this.buyingPrice = divideAndScale(rawBuying, divisor, scale);
        this.sellingPrice = divideAndScale(rawSelling, divisor, scale);
        this.effectiveBuyingPrice = divideAndScale(rawEffectiveBuying, divisor, scale);
        this.effectiveSellingPrice = divideAndScale(rawEffectiveSelling, divisor, scale);
        setLastUpdated(dataTimestamp);
    }

    @Override
    public void scaleFields(int scale) {
        this.buyingPrice = scaleValue(this.buyingPrice, scale);
        this.sellingPrice = scaleValue(this.sellingPrice, scale);
        this.effectiveBuyingPrice = scaleValue(this.effectiveBuyingPrice, scale);
        this.effectiveSellingPrice = scaleValue(this.effectiveSellingPrice, scale);
    }

    @Override
    public String getCode() {
        return currencyCode;
    }

    @Override
    public BigDecimal getPriceTry() {
        return sellingPrice;
    }

    public boolean isTradable() {
        return buyingPrice != null && sellingPrice != null;
    }

    @Override
    public String resolveDisplayName() {
        return firstNonBlank(getName(), currencyCode);
    }

    private static BigDecimal divideAndScale(BigDecimal value, BigDecimal divisor, int scale) {
        if (value == null) return null;
        if (divisor.compareTo(BigDecimal.ONE) == 0) return value.setScale(scale, RoundingMode.HALF_UP);
        return value.divide(divisor, scale, RoundingMode.HALF_UP);
    }
}
