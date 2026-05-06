package com.finance.bond.util;
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

import com.finance.bond.model.BondRateHistory;

import java.math.BigDecimal;
import java.util.List;

public final class BondRateAnalyzer {

    private BondRateAnalyzer() {}

    public static boolean hasRateChanges(List<BondRateHistory> history) {
        if (history == null || history.size() < 2) return false;

        BigDecimal firstRate = history.getFirst().getCouponRate();
        if (firstRate == null) return false;

        for (int i = 1; i < history.size(); i++) {
            BigDecimal rate = history.get(i).getCouponRate();
            if (rate == null) continue;
            if (rate.compareTo(firstRate) != 0) return true;
        }
        return false;
    }
}
