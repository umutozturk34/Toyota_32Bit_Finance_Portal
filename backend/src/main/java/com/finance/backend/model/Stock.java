package com.finance.backend.model;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;
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
}