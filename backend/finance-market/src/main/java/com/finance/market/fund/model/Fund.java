package com.finance.market.fund.model;

import com.finance.market.core.model.BaseAsset;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A TEFAS investment fund keyed by its code, holding the unit price ({@code getPriceTry()}),
 * profile/category info, and trailing returns. The unit price is stored at scale 6.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Entity
@Table(name = "funds", indexes = @Index(name = "idx_funds_fund_type", columnList = "fund_type"))
public class Fund extends BaseAsset {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "fund_code", length = 20)
    private String fundCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "fund_type", length = 20)
    private FundType fundType;

    @Column(name = "price", precision = 19, scale = 6)
    private BigDecimal price;

    @Column(name = "bulletin_price", precision = 19, scale = 4)
    private BigDecimal bulletinPrice;

    @Column(name = "share_count", precision = 19, scale = 2)
    private BigDecimal shareCount;

    @Column(name = "investor_count", precision = 19, scale = 2)
    private BigDecimal investorCount;

    @Column(name = "portfolio_size", precision = 19, scale = 2)
    private BigDecimal portfolioSize;

    @Column(name = "risk_value")
    private Integer riskValue;

    @Column(name = "sell_valor")
    private Integer sellValor;

    @Column(name = "buyback_valor")
    private Integer buybackValor;

    @Column(name = "trade_start_time", length = 8)
    private String tradeStartTime;

    @Column(name = "trade_end_time", length = 8)
    private String tradeEndTime;

    @Column(name = "category", length = 80)
    private String category;

    @Column(name = "sub_category", length = 80)
    private String subCategory;

    @Column(name = "category_rank")
    private Integer categoryRank;

    @Column(name = "category_total_funds")
    private Integer categoryTotalFunds;

    @Column(name = "market_share", precision = 9, scale = 4)
    private BigDecimal marketShare;

    @Column(name = "isin_code", length = 16)
    private String isinCode;

    @Column(name = "kap_link", length = 255)
    private String kapLink;

    // When the TEFAS profile (valör/ISIN/seans/risk) was last applied. Drives the bulk back-fill: a fund is
    // re-enriched when this is null or older than the configured refresh window, so the rarely-changing profile
    // is kept fresh without re-fetching every fund on every cycle (the per-fund TEFAS endpoint has no bulk form).
    @Column(name = "profile_enriched_at")
    private LocalDateTime profileEnrichedAt;

    @Column(name = "return_1m", precision = 12, scale = 4)
    private BigDecimal return1m;

    @Column(name = "return_3m", precision = 12, scale = 4)
    private BigDecimal return3m;

    @Column(name = "return_6m", precision = 12, scale = 4)
    private BigDecimal return6m;

    @Column(name = "return_1y", precision = 12, scale = 4)
    private BigDecimal return1y;

    @Column(name = "return_ytd", precision = 12, scale = 4)
    private BigDecimal returnYtd;

    @Column(name = "return_3y", precision = 12, scale = 4)
    private BigDecimal return3y;

    @Column(name = "return_5y", precision = 12, scale = 4)
    private BigDecimal return5y;

    /** Scales numeric fields per fund type; some types omit bulletin price/investor count. */
    public void applyScaling(FundType fundType) {
        this.price = scaleValue(this.price, 6);
        this.bulletinPrice = fundType != null && fundType.scalesBulletinPrice() ? scaleValue(this.bulletinPrice, 4) : null;
        this.shareCount = scaleValue(this.shareCount, 2);
        this.investorCount = fundType != null && fundType.scalesInvestorCount() ? scaleValue(this.investorCount, 2) : null;
        this.portfolioSize = scaleValue(this.portfolioSize, 2);
    }

    @Override
    public void scaleFields(int scale) {
        this.price = scaleValue(this.price, 6);
        this.bulletinPrice = scaleValue(this.bulletinPrice, scale);
        this.shareCount = scaleValue(this.shareCount, 2);
        this.investorCount = scaleValue(this.investorCount, 2);
        this.portfolioSize = scaleValue(this.portfolioSize, 2);
    }

    @Override
    public String getCode() {
        return fundCode;
    }

    @Override
    public BigDecimal getPriceTry() {
        return price;
    }
}
