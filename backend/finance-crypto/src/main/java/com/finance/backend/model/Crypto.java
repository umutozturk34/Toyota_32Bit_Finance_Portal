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
@Table(name = "cryptos",
    indexes = {
        @Index(name = "idx_crypto_symbol", columnList = "symbol")
    }
)
public class Crypto extends BaseAsset {
    @Id
    @EqualsAndHashCode.Include 
    @Column(name = "id")
    private String id;
    @Column(name = "symbol")
    private String symbol;
    @Column(name = "current_price", precision = 19, scale = 4)
    private BigDecimal currentPrice;
    @Column(name = "current_price_try", precision = 19, scale = 4)
    private BigDecimal currentPriceTry;
    @Column(name = "change_amount", precision = 19, scale = 4)
    private BigDecimal changeAmount;
    @Column(name = "change_percent", precision = 19, scale = 4)
    private BigDecimal changePercent;
    @Column(name = "market_cap", precision = 19, scale = 4)
    private BigDecimal marketCap;
    @Column(name = "total_volume", precision = 19, scale = 4)
    private BigDecimal totalVolume;
    @Column(name = "exchange")
    private String exchange;
    @Column(name = "currency")
    private String currency;

    public void scaleFields(int scale) {
        this.currentPrice = scaleValue(this.currentPrice, scale);
        this.currentPriceTry = scaleValue(this.currentPriceTry, scale);
        this.changeAmount = scaleValue(this.changeAmount, scale);
        this.changePercent = scaleValue(this.changePercent, scale);
        this.marketCap = scaleValue(this.marketCap, scale);
        this.totalVolume = scaleValue(this.totalVolume, scale);
    }
}