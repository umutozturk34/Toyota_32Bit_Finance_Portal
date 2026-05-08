package com.finance.market.fund.dto.external;
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
import java.time.LocalDateTime;

public record TefasFundDto(
        String fundCode,
        String name,
        LocalDateTime date,
        BigDecimal price,
        BigDecimal bulletinPrice,
        BigDecimal shareCount,
        BigDecimal investorCount,
        BigDecimal portfolioSize
) {}
