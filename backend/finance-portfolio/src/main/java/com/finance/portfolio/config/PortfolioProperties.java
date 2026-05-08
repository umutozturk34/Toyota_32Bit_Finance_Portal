package com.finance.portfolio.config;
import com.finance.common.service.MarketSnapshotProcessor;

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

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.portfolio")
public class PortfolioProperties {

    private String defaultName = "Ana Portföy";
    private String defaultCurrency = "TRY";
    private BigDecimal initialBalance = new BigDecimal("1000000.0000");
    private BigDecimal minTransactionAmountTry = new BigDecimal("10");
    private int historicalRateLookbackDays = 7;
    private LotLimits lotLimits = new LotLimits();
    private Snapshot snapshot = new Snapshot();
    private Backfill backfill = new Backfill();

    @Getter
    @Setter
    public static class LotLimits {
        private LocalDate minEntryDate = LocalDate.of(2000, 1, 1);
        private BigDecimal minPriceTry = new BigDecimal("0.0001");
        private BigDecimal maxPriceTry = new BigDecimal("1000000000");
        private BigDecimal minQuantity = new BigDecimal("0.00000001");
        private BigDecimal maxQuantity = new BigDecimal("1000000000");
    }

    @Getter
    @Setter
    public static class Snapshot {
        private int dailyLookbackHours = 24;
    }

    @Getter
    @Setter
    public static class Backfill {
        private int priceLookbackDays = 7;
        private int lockStripes = 32;
    }
}
