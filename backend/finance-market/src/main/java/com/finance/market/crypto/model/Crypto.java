package com.finance.market.crypto.model;

import com.finance.market.core.model.BaseAsset;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;
/**
 * A cryptocurrency keyed by its CoinGecko id, holding both the native price ({@code currentPrice},
 * usually USD) and the TRY-converted price ({@code currentPriceTry}); {@code getPriceTry()} returns
 * the latter.
 */
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
    @Column(name = "market_cap", precision = 19, scale = 4)
    private BigDecimal marketCap;
    @Column(name = "total_volume", precision = 19, scale = 4)
    private BigDecimal totalVolume;
    @Column(name = "exchange")
    private String exchange;
    @Column(name = "currency")
    private String currency;

    /**
     * Rounds every monetary attribute of this coin in place to the given scale, including both
     * price representations, the inherited change amount/percent, market cap and total volume.
     * Used to normalize precision before persistence or serialization.
     *
     * @param scale the number of decimal places to round each value to
     */
    @Override
    public void scaleFields(int scale) {
        this.currentPrice = scaleValue(this.currentPrice, scale);
        this.currentPriceTry = scaleValue(this.currentPriceTry, scale);
        setChangeAmount(scaleValue(getChangeAmount(), scale));
        setChangePercent(scaleValue(getChangePercent(), scale));
        this.marketCap = scaleValue(this.marketCap, scale);
        this.totalVolume = scaleValue(this.totalVolume, scale);
    }

    /**
     * Satisfies the {@code BaseAsset} contract by exposing the CoinGecko id as this asset's
     * canonical code, since the id is the coin's stable identifier.
     *
     * @return the CoinGecko coin id
     */
    @Override
    public String getCode() {
        return id;
    }

    /**
     * Implements the {@code BaseAsset} TRY-price contract by returning the pre-converted
     * {@code currentPriceTry} rather than the native ({@code currentPrice}, usually USD) value,
     * so callers always get the coin's value in Turkish lira.
     *
     * @return the current price expressed in TRY
     */
    @Override
    public BigDecimal getPriceTry() {
        return currentPriceTry;
    }

    /**
     * Resolves the label shown to users by falling back through progressively less descriptive
     * identifiers: the explicit name first, then the ticker symbol, and finally the id, so a
     * non-blank label is always produced even for partially populated coins.
     *
     * @return the first non-blank of name, symbol, or id
     */
    @Override
    public String resolveDisplayName() {
        return firstNonBlank(getName(), symbol, id);
    }
}