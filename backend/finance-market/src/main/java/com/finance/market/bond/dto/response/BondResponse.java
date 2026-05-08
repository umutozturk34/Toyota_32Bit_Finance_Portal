package com.finance.market.bond.dto.response;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BondResponse(
        String seriesCode,
        String isinCode,
        BigDecimal couponRate,
        BigDecimal simpleYield,
        BigDecimal baseIndex,
        LocalDate maturityStart,
        LocalDate maturityEnd,
        LocalDate nextCouponDate,
        String bondType,
        String issuer,
        LocalDateTime lastUpdated
) {}
