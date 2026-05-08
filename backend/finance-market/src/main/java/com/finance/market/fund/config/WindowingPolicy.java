package com.finance.market.fund.config;
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

public record WindowingPolicy(
        int windowSizeDays,
        int yearsToFetch,
        int minCandlesForIncremental,
        int eodCutoverHour) {

    public static WindowingPolicy from(FundProperties props) {
        return new WindowingPolicy(
                props.getWindowSizes(),
                props.getYearsToFetch(),
                props.getMinCandlesForIncremental(),
                props.getTefasEodCutoverHour());
    }
}
