package com.finance.market.crypto.config;
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
@ConfigurationProperties(prefix = "app.crypto")
public class CryptoProperties {

    private int historyDays = 365;
    private int minCandlesForHealthy = 350;
    private int batchMinSample = 5;
    private int binancePageSize = 1000;
    private String binanceInterval = "1d";
    private String vsUsd = "usd";
    private String vsTry = "try";
}
