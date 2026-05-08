package com.finance.market.bond.model;
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

public enum BondType {
    DISCOUNTED,
    FIXED_COUPON,
    FLOATING_TLREF,
    FLOATING_CPI,
    FLOATING_AUCTION,
    SUKUK_FIXED,
    SUKUK_CPI;

    public boolean isFloating() {
        return this == FLOATING_TLREF || this == FLOATING_CPI
                || this == FLOATING_AUCTION || this == SUKUK_CPI;
    }
}
