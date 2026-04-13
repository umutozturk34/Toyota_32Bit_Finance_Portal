package com.finance.backend.model;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;
import java.math.RoundingMode;
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Entity
@Table(name = "stocks",
    indexes = {
        @Index(name = "idx_stocks_exchange", columnList = "exchange")
    }
)
public class Stock extends BaseAsset {
    @Id
    @EqualsAndHashCode.Include
    @Column(name = "symbol", length = 20)
    private String symbol;
    @Column(name = "current_price", precision = 19, scale = 4)
    private BigDecimal currentPrice;
    @Column(name = "previous_close", precision = 19, scale = 4)
    private BigDecimal previousClose;
    @Column(name = "open_price", precision = 19, scale = 4)
    private BigDecimal openPrice;
    @Column(name = "day_high", precision = 19, scale = 4)
    private BigDecimal dayHigh;
    @Column(name = "day_low", precision = 19, scale = 4)
    private BigDecimal dayLow;
    @Column(name = "volume")
    private Long volume;
    @Column(name = "price_change_percent", precision = 19, scale = 4)
    private BigDecimal priceChangePercent;
    @Column(name = "price_change_amount", precision = 19, scale = 4)
    private BigDecimal priceChangeAmount;
    @Column(name = "exchange")
    private String exchange;
    @Column(name = "currency")
    private String currency;
    @Column(name = "stock_segment", length = 50)
    @Enumerated(EnumType.STRING)
    private StockSegment stockSegment;

    public void scaleAndComputeChange(int scale) {
        this.currentPrice = scaleValue(this.currentPrice, scale);
        this.previousClose = scaleValue(this.previousClose, scale);
        this.openPrice = scaleValue(this.openPrice, scale);
        this.dayHigh = scaleValue(this.dayHigh, scale);
        this.dayLow = scaleValue(this.dayLow, scale);
        computePriceChange(scale);
    }

    private void computePriceChange(int scale) {
        if (currentPrice == null || previousClose == null || previousClose.signum() == 0) {
            this.priceChangeAmount = null;
            this.priceChangePercent = null;
            return;
        }
        BigDecimal change = currentPrice.subtract(previousClose);
        this.priceChangeAmount = change.setScale(scale, RoundingMode.HALF_UP);
        this.priceChangePercent = change.divide(previousClose, scale + 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(scale, RoundingMode.HALF_UP);
    }
}