package com.finance.stock.model;
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
    @Column(name = "exchange")
    private String exchange;
    @Column(name = "currency")
    private String currency;
    @Column(name = "stock_segment", length = 50)
    @Enumerated(EnumType.STRING)
    private StockSegment stockSegment;

    @Override
    public String getCode() {
        return symbol;
    }

    @Override
    public BigDecimal getPriceTry() {
        return currentPrice;
    }

    @Override
    public void scaleFields(int scale) {
        this.currentPrice = scaleValue(this.currentPrice, scale);
        this.previousClose = scaleValue(this.previousClose, scale);
        this.openPrice = scaleValue(this.openPrice, scale);
        this.dayHigh = scaleValue(this.dayHigh, scale);
        this.dayLow = scaleValue(this.dayLow, scale);
    }
}