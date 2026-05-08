package com.finance.market.fund.model;
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
@Table(name = "funds")
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
