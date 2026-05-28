package com.finance.market.viop.port;

import com.finance.market.viop.dto.ViopContractSpec;
import com.finance.market.viop.dto.ViopHistoryPoint;
import com.finance.market.viop.dto.ViopQuoteSnapshot;
import com.finance.market.viop.model.ViopHistoryResolution;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port to the upstream VIOP data source for contract definitions, live quotes, and
 * historical bars, isolating the rest of the module from the broker integration.
 */
public interface ViopMarketDataPort {

    /** Tradable future contract specs (one per listed future). */
    List<ViopContractSpec> fetchFutureContractSpecs();

    /** Option contract templates (underlying/expiry families) from which concrete strikes are derived. */
    List<ViopContractSpec> fetchOptionContractTemplates();

    List<ViopQuoteSnapshot> fetchAllLiveSnapshots();

    ViopQuoteSnapshot fetchSnapshot(String symbol);

    /** Historical bars for a symbol at the given resolution over {@code [from, to]}. */
    List<ViopHistoryPoint> fetchHistory(String symbol,
                                       ViopHistoryResolution resolution,
                                       Instant from,
                                       Instant to);
}
