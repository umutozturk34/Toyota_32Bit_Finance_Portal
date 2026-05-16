package com.finance.market.viop.port;

import com.finance.market.viop.dto.ViopContractSpec;
import com.finance.market.viop.dto.ViopHistoryPoint;
import com.finance.market.viop.dto.ViopQuoteSnapshot;
import com.finance.market.viop.model.ViopHistoryResolution;

import java.time.Instant;
import java.util.List;

public interface ViopMarketDataPort {

    List<ViopContractSpec> fetchFutureContractSpecs();

    List<ViopContractSpec> fetchOptionContractTemplates();

    List<ViopQuoteSnapshot> fetchAllLiveSnapshots();

    ViopQuoteSnapshot fetchSnapshot(String symbol);

    List<ViopHistoryPoint> fetchHistory(String symbol,
                                       ViopHistoryResolution resolution,
                                       Instant from,
                                       Instant to);
}
