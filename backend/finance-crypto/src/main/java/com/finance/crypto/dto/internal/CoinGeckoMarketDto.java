package com.finance.crypto.dto.internal;
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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record CoinGeckoMarketDto(
        String id,
        String symbol,
        String name,
        String image,
        @JsonProperty("current_price") BigDecimal currentPrice,
        @JsonProperty("price_change_24h") BigDecimal priceChange24h,
        @JsonProperty("price_change_percentage_24h") BigDecimal priceChangePercentage24h,
        @JsonProperty("market_cap") BigDecimal marketCap,
        @JsonProperty("total_volume") BigDecimal totalVolume
) {}
