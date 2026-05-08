package com.finance.market.forex.model;

import com.finance.market.core.model.BaseAsset;
import com.finance.common.model.*;
import com.finance.common.model.value.*;
import com.finance.common.dto.*;
import com.finance.common.dto.external.*;
import com.finance.common.dto.internal.*;
import com.finance.common.dto.request.*;
import com.finance.common.dto.response.*;
import com.finance.common.exception.*;
import com.finance.common.util.*;
import com.finance.common.service.*;
import com.finance.market.core.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.market.core.scheduler.*;
import com.finance.common.event.*;
import com.finance.market.core.mapper.*;
import com.finance.common.repository.*;
import com.finance.market.core.client.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.finance.market.forex.dto.external.TcmbRateDto;
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
    @Column(name = "cross_rate_usd", precision = 19, scale = 4)
    private BigDecimal crossRateUsd;
    @Column(name = "cross_rate_other", precision = 19, scale = 4)
    private BigDecimal crossRateOther;
    @Column(name = "open_price", precision = 19, scale = 4)
    private BigDecimal openPrice;
    @Column(name = "day_high", precision = 19, scale = 4)
    private BigDecimal dayHigh;
    @Column(name = "day_low", precision = 19, scale = 4)
    private BigDecimal dayLow;
    @Column(name = "volume")
    private Long volume;
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

    public void applyTcmbData(TcmbRateDto dto, int scale) {
        this.currencyName = dto.currencyName();
        this.currencyNameTr = dto.currencyNameTr();
        this.unit = dto.unit();
        this.forexBuying = divideByUnit(dto.forexBuying(), dto.unit(), scale);
        this.forexSelling = divideByUnit(dto.forexSelling(), dto.unit(), scale);
        this.banknoteBuying = divideByUnit(dto.banknoteBuying(), dto.unit(), scale);
        this.banknoteSelling = divideByUnit(dto.banknoteSelling(), dto.unit(), scale);
        this.crossRateUsd = divideByUnit(dto.crossRateUsd(), dto.unit(), scale);
        this.crossRateOther = dto.crossRateOther();
        this.tcmbUpdatedAt = LocalDateTime.now();
    }

    public void applyYahooSnapshot(BigDecimal marketPrice, BigDecimal previousClose,
                                   BigDecimal openPrice, BigDecimal dayHigh, BigDecimal dayLow,
                                   Long volume, BigDecimal spreadRate, int scale) {
        if (marketPrice == null) return;
        this.currentPrice = scaleValue(marketPrice, scale);
        this.sellingPrice = scaleValue(marketPrice.multiply(BigDecimal.ONE.add(spreadRate)), scale);
        this.openPrice = scaleValue(openPrice, scale);
        this.dayHigh = scaleValue(dayHigh, scale);
        this.dayLow = scaleValue(dayLow, scale);
        this.volume = volume;
        applyChange(marketPrice, previousClose, scale);
        this.yahooUpdatedAt = LocalDateTime.now();
    }

    public void applySyntheticPrice(BigDecimal syntheticPrice, BigDecimal syntheticPreviousClose,
                                    BigDecimal openPrice, BigDecimal dayHigh, BigDecimal dayLow,
                                    BigDecimal spreadRate, int scale) {
        if (syntheticPrice == null) return;
        this.currentPrice = scaleValue(syntheticPrice, scale);
        this.sellingPrice = scaleValue(syntheticPrice.multiply(BigDecimal.ONE.add(spreadRate)), scale);
        this.openPrice = scaleValue(openPrice, scale);
        this.dayHigh = scaleValue(dayHigh, scale);
        this.dayLow = scaleValue(dayLow, scale);
        if (syntheticPreviousClose != null) {
            applyChange(syntheticPrice, syntheticPreviousClose, scale);
        }
        this.yahooUpdatedAt = LocalDateTime.now();
    }

    @Override
    public void scaleFields(int scale) {
        this.currentPrice = scaleValue(this.currentPrice, scale);
        this.sellingPrice = scaleValue(this.sellingPrice, scale);
        this.openPrice = scaleValue(this.openPrice, scale);
        this.dayHigh = scaleValue(this.dayHigh, scale);
        this.dayLow = scaleValue(this.dayLow, scale);
        this.forexBuying = scaleValue(this.forexBuying, scale);
        this.forexSelling = scaleValue(this.forexSelling, scale);
        this.banknoteBuying = scaleValue(this.banknoteBuying, scale);
        this.banknoteSelling = scaleValue(this.banknoteSelling, scale);
        this.crossRateUsd = scaleValue(this.crossRateUsd, scale);
    }

    private BigDecimal divideByUnit(BigDecimal value, int unit, int scale) {
        if (value == null || unit <= 1) return value;
        return value.divide(new BigDecimal(unit), scale, RoundingMode.HALF_UP);
    }

    @Override
    public String getCode() {
        return currencyCode;
    }

    @Override
    public BigDecimal getPriceTry() {
        return sellingPrice != null ? sellingPrice : currentPrice;
    }

    @Override
    public String resolveDisplayName() {
        return firstNonBlank(getName(), currencyNameTr, currencyName, currencyCode);
    }
}