package com.finance.market.bond.model;

import com.finance.market.core.model.BaseAsset;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.finance.market.bond.util.BondTypeResolver;
import com.finance.market.bond.util.BondYieldCalculator;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A government/sukuk/discount bond keyed by series code, with coupon/yield/maturity terms and a
 * derived {@link BondType}. Bonds are not directly priced ({@code getPriceTry()} is null); they are
 * valued via yield/rate rather than a TRY price.
 */
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
        return bondType != null && bondType.isFloating();
    }

    /** Sets the next semi-annual coupon date after today (clamped to maturity), or null if matured. */
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

    public void resolveType(List<BondRateHistory> history,
                             BigDecimal auctionThreshold, BigDecimal cpiFixedThreshold) {
        this.bondType = BondTypeResolver.resolve(this, history, auctionThreshold, cpiFixedThreshold);
    }

    public void resolveSimpleYield(BigDecimal faceValue, int daysInYear) {
        this.simpleYield = BondYieldCalculator.compute(this, faceValue, daysInYear);
    }

    @Override
    public void scaleFields(int scale) {
        this.couponRate = scaleValue(this.couponRate, scale);
        this.simpleYield = scaleValue(this.simpleYield, scale);
        this.baseIndex = scaleValue(this.baseIndex, scale);
    }

    @Override
    public String getCode() {
        return seriesCode;
    }

    @Override
    public BigDecimal getPriceTry() {
        return null;
    }
}
