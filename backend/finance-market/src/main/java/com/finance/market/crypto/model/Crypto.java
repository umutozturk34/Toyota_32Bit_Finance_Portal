package com.finance.market.crypto.model;
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
import com.finance.common.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.scheduler.*;
import com.finance.common.event.*;
import com.finance.common.mapper.*;
import com.finance.common.repository.*;
import com.finance.common.client.*;
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
    @Column(name = "market_cap", precision = 19, scale = 4)
    private BigDecimal marketCap;
    @Column(name = "total_volume", precision = 19, scale = 4)
    private BigDecimal totalVolume;
    @Column(name = "exchange")
    private String exchange;
    @Column(name = "currency")
    private String currency;

    @Override
    public void scaleFields(int scale) {
        this.currentPrice = scaleValue(this.currentPrice, scale);
        this.currentPriceTry = scaleValue(this.currentPriceTry, scale);
        setChangeAmount(scaleValue(getChangeAmount(), scale));
        setChangePercent(scaleValue(getChangePercent(), scale));
        this.marketCap = scaleValue(this.marketCap, scale);
        this.totalVolume = scaleValue(this.totalVolume, scale);
    }

    @Override
    public String getCode() {
        return id;
    }

    @Override
    public BigDecimal getPriceTry() {
        return currentPriceTry;
    }

    @Override
    public String resolveDisplayName() {
        return firstNonBlank(getName(), symbol, id);
    }
}