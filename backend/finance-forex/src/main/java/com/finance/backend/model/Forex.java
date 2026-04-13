package com.finance.backend.model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.finance.backend.dto.external.TcmbRateDto;
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
        this.currentPrice = syntheticPrice;
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

    private BigDecimal divideByUnit(BigDecimal value, int unit, int scale) {
        if (value == null || unit <= 1) return value;
        return value.divide(new BigDecimal(unit), scale, RoundingMode.HALF_UP);
    }
}