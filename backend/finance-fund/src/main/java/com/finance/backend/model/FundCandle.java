package com.finance.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "fund_candles",
    uniqueConstraints = @UniqueConstraint(
        name = "uc_fund_code_date",
        columnNames = {"fund_code", "candle_date"}
    ),
    indexes = {
        @Index(name = "idx_fund_candles_code", columnList = "fund_code"),
        @Index(name = "idx_fund_candles_date", columnList = "candle_date"),
        @Index(name = "idx_fund_candles_code_date", columnList = "fund_code, candle_date")
    }
)
public class FundCandle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_code", referencedColumnName = "fund_code", nullable = false,
            foreignKey = @ForeignKey(name = "fk_fund_candle_code"))
    @JsonIgnore
    private Fund fund;

    @Column(name = "fund_code", insertable = false, updatable = false, nullable = false)
    private String fundCode;

    @Column(name = "fund_type", length = 20)
    private String fundType;

    @Column(name = "candle_date", nullable = false)
    private LocalDateTime candleDate;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FundCandle that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    public void applyScaling(String fundType) {
        this.price = scaleValue(this.price, 6);
        this.bulletinPrice = "BYF".equals(fundType) ? scaleValue(this.bulletinPrice, 4) : null;
        this.shareCount = scaleValue(this.shareCount, 2);
        this.investorCount = "YAT".equals(fundType) ? scaleValue(this.investorCount, 2) : null;
        this.portfolioSize = scaleValue(this.portfolioSize, 2);
    }

    private static BigDecimal scaleValue(BigDecimal value, int scale) {
        return value != null ? value.setScale(scale, RoundingMode.HALF_UP) : null;
    }

    public void scaleAllFields() {
        this.price = scaleValue(this.price, 6);
        this.bulletinPrice = scaleValue(this.bulletinPrice, 4);
        this.shareCount = scaleValue(this.shareCount, 2);
        this.investorCount = scaleValue(this.investorCount, 2);
        this.portfolioSize = scaleValue(this.portfolioSize, 2);
    }
}
