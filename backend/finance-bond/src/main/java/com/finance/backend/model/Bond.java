package com.finance.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.finance.backend.util.BondRateAnalyzer;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Log4j2
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Table(name = "bonds",
    indexes = {
        @Index(name = "idx_bond_isin", columnList = "isin_code"),
        @Index(name = "idx_bond_type", columnList = "bond_type")
    }
)
public class Bond extends BaseAsset {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "series_code", length = 50)
    private String seriesCode;

    @Column(name = "isin_code", length = 50, unique = true)
    private String isinCode;

    @JsonIgnore
    @OneToMany(mappedBy = "bond", fetch = FetchType.LAZY)
    private List<BondRateHistory> rateHistory;

    @Column(name = "coupon_rate", precision = 10, scale = 4)
    private BigDecimal couponRate;

    @Column(name = "simple_yield", precision = 19, scale = 4)
    private BigDecimal simpleYield;

    @Column(name = "base_index", precision = 19, scale = 4)
    private BigDecimal baseIndex;

    @Column(name = "maturity_start")
    private LocalDate maturityStart;

    @Column(name = "maturity_end")
    private LocalDate maturityEnd;

    @Column(name = "next_coupon_date")
    private LocalDate nextCouponDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "bond_type", length = 30)
    private BondType bondType;

    @Column(name = "issuer", length = 50)
    private String issuer;

    @JsonIgnore
    public boolean isDiscounted() {
        return bondType == BondType.DISCOUNTED;
    }

    @JsonIgnore
    public boolean isFloating() {
        return bondType == BondType.FLOATING_TLREF || bondType == BondType.FLOATING_CPI || bondType == BondType.FLOATING_AUCTION;
    }

    @JsonIgnore
    public boolean isSukuk() {
        return bondType == BondType.SUKUK_FIXED || bondType == BondType.SUKUK_CPI;
    }

    @JsonIgnore
    public boolean isExpired() {
        return maturityEnd != null && !maturityEnd.isAfter(LocalDate.now());
    }

    @JsonIgnore
    public long daysToMaturity() {
        if (maturityEnd == null) return -1;
        return ChronoUnit.DAYS.between(LocalDate.now(), maturityEnd);
    }

    @JsonIgnore
    public long daysToNextCoupon() {
        if (nextCouponDate == null) return -1;
        return ChronoUnit.DAYS.between(LocalDate.now(), nextCouponDate);
    }

    public void resolveNextCouponDate() {
        if (maturityStart == null) {
            this.nextCouponDate = null;
            return;
        }
        LocalDate today = LocalDate.now();
        if (maturityEnd != null && maturityEnd.isBefore(today)) {
            this.nextCouponDate = null;
            return;
        }
        LocalDate couponDate = maturityStart;
        while (!couponDate.isAfter(today)) {
            couponDate = couponDate.plusMonths(6);
        }
        if (maturityEnd != null && couponDate.isAfter(maturityEnd)) {
            couponDate = maturityEnd;
        }
        this.nextCouponDate = couponDate;
    }

    public void resolveType(List<BondRateHistory> history, BigDecimal rateThreshold, BigDecimal auctionThreshold, BigDecimal cpiFixedThreshold) {
        if (isinCode == null) {
            log.warn("Null ISIN on bond {}, defaulting to FIXED_COUPON", seriesCode);
            this.bondType = BondType.FIXED_COUPON;
            return;
        }

        if (isinCode.startsWith("TRB")) {
            this.bondType = BondType.DISCOUNTED;
            log.debug("Bond {} classified as DISCOUNTED (TRB prefix)", isinCode);
            return;
        }

        if (couponRate == null || couponRate.compareTo(BigDecimal.ZERO) == 0) {
            this.bondType = BondType.DISCOUNTED;
            log.debug("Bond {} classified as DISCOUNTED (rate is null or zero)", isinCode);
            return;
        }

        boolean sukuk = isinCode.startsWith("TRD");

        if (couponRate.compareTo(cpiFixedThreshold) < 0) {
            this.bondType = sukuk ? BondType.SUKUK_CPI : BondType.FLOATING_CPI;
            log.debug("Bond {} classified as {} (rate {} < cpiFixedThreshold {})",
                    isinCode, bondType, couponRate, cpiFixedThreshold);
            return;
        }

        boolean rateChanges = BondRateAnalyzer.hasRateChanges(history);

        if (sukuk) {
            this.bondType = rateChanges ? BondType.SUKUK_CPI : BondType.SUKUK_FIXED;
        } else if (rateChanges) {
            this.bondType = couponRate.compareTo(auctionThreshold) >= 0
                    ? BondType.FLOATING_AUCTION
                    : BondType.FLOATING_TLREF;
        } else {
            this.bondType = BondType.FIXED_COUPON;
        }

        log.debug("Bond {} classified as {} (sukuk={}, rateChanges={}, rate={}, historySize={})",
                isinCode, bondType, sukuk, rateChanges, couponRate, history != null ? history.size() : 0);
    }

    public void resolveSimpleYield(BigDecimal faceValue, int daysInYear) {
        if (baseIndex == null || baseIndex.compareTo(BigDecimal.ZERO) == 0) {
            log.debug("Cannot calculate yield for {}: baseIndex is null or zero", isinCode);
            this.simpleYield = null;
            return;
        }

        BigDecimal daysPerYear = new BigDecimal(daysInYear);

        if (bondType == BondType.DISCOUNTED) {
            if (maturityEnd == null) {
                log.debug("Cannot calculate discounted yield for {}: maturityEnd is null", isinCode);
                this.simpleYield = null;
                return;
            }
            long days = daysToMaturity();
            if (days <= 0) {
                log.debug("Cannot calculate discounted yield for {}: bond expired (days={})", isinCode, days);
                this.simpleYield = null;
                return;
            }
            BigDecimal daysDecimal = new BigDecimal(days);
            this.simpleYield = faceValue.subtract(baseIndex)
                    .divide(baseIndex, 10, RoundingMode.HALF_UP)
                    .multiply(daysPerYear)
                    .divide(daysDecimal, 10, RoundingMode.HALF_UP)
                    .multiply(faceValue)
                    .setScale(4, RoundingMode.HALF_UP);
            log.debug("Discounted yield for {}: {} (baseIndex={}, days={})", isinCode, simpleYield, baseIndex, days);
            return;
        }

        if (isFloating()) {
            log.debug("Floating bond {}, yield not applicable", isinCode);
            this.simpleYield = null;
            return;
        }

        if (couponRate == null) {
            log.debug("Cannot calculate fixed yield for {}: couponRate is null", isinCode);
            this.simpleYield = null;
            return;
        }

        BigDecimal annualCoupon = couponRate.multiply(new BigDecimal("2"));
        this.simpleYield = annualCoupon
                .divide(baseIndex, 10, RoundingMode.HALF_UP)
                .multiply(faceValue)
                .setScale(4, RoundingMode.HALF_UP);
        log.debug("Fixed coupon yield for {}: {} (couponRate={}, baseIndex={})", isinCode, simpleYield, couponRate, baseIndex);
    }

    public void scaleFields(int scale) {
        this.couponRate = scaleValue(this.couponRate, scale);
        this.simpleYield = scaleValue(this.simpleYield, scale);
        this.baseIndex = scaleValue(this.baseIndex, scale);
    }

    private BigDecimal scaleValue(BigDecimal value, int scale) {
        return value != null ? value.setScale(scale, RoundingMode.HALF_UP) : null;
    }
}
