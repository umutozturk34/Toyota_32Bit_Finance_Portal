package com.finance.market.bond.service;

import com.finance.common.model.MarketType;
import com.finance.market.bond.dto.response.BondRateResponse;
import com.finance.market.bond.repository.BondRateHistoryRepository;
import com.finance.market.bond.repository.BondRepository;
import com.finance.market.core.service.MarketHistoryProvider;
import com.finance.shared.model.CandlePeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Serves a bond's TRY clean-price history on the unified {@code /market/history} endpoint so the market
 * overview asset-card sparkline can plot it instead of 404ing (bonds had no {@link MarketHistoryProvider}).
 * The card holds the bond's series code (the {@code Bond} id); rate history is keyed by ISIN, so the series
 * is resolved to its ISIN first. The {@code price} per {@code rateDate} is already in TRY — bonds are never
 * FX-converted — so the points are returned verbatim.
 */
@Service
@RequiredArgsConstructor
public class BondHistoryProvider implements MarketHistoryProvider {

    private final BondRepository bondRepository;
    private final BondRateHistoryRepository rateHistoryRepository;

    @Override
    public MarketType getMarketType() {
        return MarketType.BOND;
    }

    @Override
    public List<BondRateResponse> getHistory(String code, CandlePeriod period) {
        return fetch(code, period.toStartDate(), null);
    }

    @Override
    public List<BondRateResponse> getHistoryInRange(String code, LocalDate from, LocalDate to) {
        return fetch(code, from, to);
    }

    private List<BondRateResponse> fetch(String seriesCode, LocalDate from, LocalDate to) {
        return bondRepository.findById(seriesCode)
                .map(bond -> rateHistoryRepository
                        // RateDateAfter is exclusive; step back one day so `from` itself is included.
                        .findByIsinCodeAndRateDateAfterOrderByRateDateAsc(bond.getIsinCode(), from.minusDays(1))
                        .stream()
                        .filter(h -> h.getPrice() != null)
                        .filter(h -> to == null || !h.getRateDate().isAfter(to))
                        .map(h -> new BondRateResponse(h.getRateDate(), h.getCouponRate(), h.getPrice()))
                        .toList())
                .orElseGet(List::of);
    }
}
