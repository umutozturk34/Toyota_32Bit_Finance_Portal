package com.finance.market.fund.config;
import com.finance.market.core.service.MarketSnapshotProcessor;

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
import com.finance.market.core.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.market.core.scheduler.*;
import com.finance.common.event.*;
import com.finance.market.core.mapper.*;
import com.finance.common.repository.*;
import com.finance.market.core.client.*;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.fund")
public class FundProperties {

    private int yearsToFetch = 5;
    private int minCandlesForIncremental = 30;
    private int windowSizes = 28;
    private int tefasEodCutoverHour = 11;
    private int tefasMaxResponseMb = 32;
    private int tefasBulkPageSize = 100_000;
    private int tefasDefaultPageSize = 100;
    private String tefasLanguage = "TR";
    private String tefasUserAgent =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0 Safari/537.36";
    private int autoTrackSortOrder = 9999;
    private int holidayLookbackDays = 5;
}
