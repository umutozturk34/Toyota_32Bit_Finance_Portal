package com.finance.portfolio.service.summary;

import com.finance.portfolio.service.pricing.DerivativePositionFormatter;
import com.finance.portfolio.service.pricing.MultiCurrencyPnlCalculator;
import com.finance.portfolio.service.pricing.RealReturnCalculator;

import com.finance.shared.service.AssetPricingPort;
import com.finance.portfolio.dto.response.AllocationItem;
import com.finance.portfolio.dto.response.AssetAggregateResponse;
import com.finance.portfolio.dto.response.CurrencyFramePct;
import com.finance.common.dto.response.PagedResponse;
import com.finance.portfolio.dto.response.PortfolioSummaryResponse;
import com.finance.portfolio.dto.response.PositionResponse;
import com.finance.portfolio.mapper.PortfolioResponseMapper;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import com.finance.shared.service.AssetPricingPort.AssetKey;
import com.finance.shared.service.AssetPricingPort.AssetMeta;
import com.finance.shared.service.AssetPricingPort.PriceBundle;
import com.finance.shared.util.EnumParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-side queries that value a portfolio live and in TRY: position lists (spot + derivatives,
 * filterable/sortable/paged), the headline summary (total value/cost/PnL, daily PnL, real return,
 * multi-currency frames), per-asset aggregates across lots, and allocation. Spot uses current prices
 * from the pricing port; derivatives add live-to-TRY notional and unrealized PnL. Daily PnL prefers
 * the latest per-asset snapshot deltas, falling back to the portfolio snapshot day-over-day diff.
 */
@Service
@RequiredArgsConstructor
public class PortfolioSummaryService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final AssetPricingPort pricingPort;
    private final PortfolioPositionRepository positionRepository;
    private final PortfolioResponseMapper responseMapper;
    private final DerivativePositionRepository derivativePositionRepository;
    private final AllocationCalculator allocationCalculator;
    private final DerivativePositionFormatter derivativePositionFormatter;
    private final DerivativeAggregationService derivativeAggregationService;
    private final PositionRowBuilder positionRowBuilder;
    private final SpotAggregationService spotAggregationService;
    private final DailyAggregationService dailyAggregationService;
    private final RealReturnCalculator realReturnCalculator;
    private final MultiCurrencyPnlCalculator multiCurrencyPnlCalculator;
    private final SummaryEntryFootprintBuilder summaryFootprintBuilder;
    private final ViopAssetAggregateService viopAssetAggregateService;
    private final PerCurrencyFrameOverrideService frameOverrideService;

    /** All position rows (spot lots valued at live prices, plus formatted derivative rows) for the portfolio. */
    @Transactional(readOnly = true)
    public List<PositionResponse> getPositions(Long portfolioId) {
        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(portfolioId);
        // Open rows mark-to-market at the CURRENT price (forex SELLING rate, same as the card + snapshot);
        // closed rows use their stored exit price. Consistent valuation everywhere; spread only on entry+exit.
        Map<AssetKey, PriceBundle> bundles = pricingPort.getBundles(positionRowBuilder.toKeys(positions));
        Map<Long, BigDecimal> currentValuesById = spotAggregationService.currentValuesByPositionId(positions, bundles);
        Map<Long, BigDecimal> realPctById = realReturnCalculator.computePerPosition(positions, currentValuesById);
        List<PositionResponse> spotRows = positions.stream()
                .map(pos -> positionRowBuilder.toPositionResponse(pos, bundles.get(pos.toAssetKey()), realPctById.get(pos.getId())))
                .toList();
        List<PositionResponse> derivativeRows = derivativePositionRepository.findByPortfolioId(portfolioId).stream()
                .map(this::toDerivativePositionResponse)
                .filter(r -> r != null)
                .toList();
        if (derivativeRows.isEmpty()) return spotRows;
        java.util.List<PositionResponse> combined = new java.util.ArrayList<>(spotRows.size() + derivativeRows.size());
        combined.addAll(spotRows);
        combined.addAll(derivativeRows);
        return combined;
    }

    private PositionResponse toDerivativePositionResponse(DerivativePosition position) {
        return derivativePositionFormatter.toPositionResponse(position);
    }

    /** Returns every lot for a given asset type+code in the portfolio, unpaged. Drives the asset detail
     *  page where lot count is small (typically &lt;30) but can exceed any reasonable page size. */
    @Transactional(readOnly = true)
    public List<PositionResponse> getPositionsByAsset(Long portfolioId, String assetType, String assetCode) {
        return getPositions(portfolioId).stream()
                .filter(r -> assetType.equalsIgnoreCase(r.assetType())
                        && assetCode.equalsIgnoreCase(r.assetCode()))
                .toList();
    }

    /**
     * Position rows filtered (status/type/search), sorted, then paged in memory — filtering precedes
     * pagination so a {@code closed} or {@code assetType} match on a later page is never dropped.
     */
    @Transactional(readOnly = true)
    public PagedResponse<PositionResponse> getPositionsPaged(Long portfolioId, String search,
                                                               String assetType, String sortBy, String direction,
                                                               Boolean closed, int page, int size) {
        List<PositionResponse> all = getPositions(portfolioId);

        // Status filter must run before pagination — client-side filtering would only see
        // the current page, hiding any closed lots that fall on later pages.
        if (closed != null) {
            all = all.stream().filter(r -> isPositionClosed(r) == closed).toList();
        }
        if (assetType != null && !assetType.isBlank()) {
            all = all.stream().filter(r -> assetType.equalsIgnoreCase(r.assetType())).toList();
        }
        if (search != null && !search.isBlank()) {
            String lower = search.toLowerCase();
            all = all.stream()
                    .filter(r -> r.assetCode().toLowerCase().contains(lower)
                            || (r.assetName() != null && r.assetName().toLowerCase().contains(lower)))
                    .toList();
        }
        if (sortBy != null && !sortBy.isBlank()) {
            Comparator<PositionResponse> comparator = positionRowBuilder.buildPositionComparator(sortBy);
            all = "asc".equalsIgnoreCase(direction)
                    ? all.stream().sorted(comparator).toList()
                    : all.stream().sorted(comparator.reversed()).toList();
        }

        long total = all.size();
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return PagedResponse.of(all.subList(from, to), page, size, total);
    }

    /**
     * Headline figures in TRY, optionally scoped to one asset type. A null/blank type covers the whole
     * portfolio and folds open-derivative notional+PnL into the totals; a {@code VIOP} filter reports
     * derivatives only; any other type reports spot for that type alone.
     */
    private static boolean isPositionClosed(PositionResponse r) {
        // Both spot and derivative responses populate exitDate when the position is closed —
        // spot from PortfolioPosition.exitDate, derivative from DerivativePosition.closeDate
        // via DerivativePositionFormatter. No need to special-case VIOP.
        return r.exitDate() != null;
    }

    /**
     * Headline figures in TRY, optionally scoped to one asset type. A null/blank type covers the whole
     * portfolio and folds open-derivative notional+PnL into the totals; a {@code VIOP} filter reports
     * derivatives only; any other type reports spot for that type alone. Real (inflation-adjusted) return
     * excludes VIOP, while the per-currency frames span both.
     */
    @Transactional(readOnly = true)
    public PortfolioSummaryResponse getSummary(Long portfolioId, String assetType) {
        List<PortfolioPosition> positions = positionRowBuilder.filterByType(
                positionRepository.findByPortfolioId(portfolioId), assetType);
        SpotTotals spot = spotAggregationService.aggregateSpotTotals(positions);
        SummaryTotals totals = applyDerivativeAdjustment(portfolioId, assetType, spot);
        BigDecimal pnlPercent = totals.capitalBase.compareTo(BigDecimal.ZERO) > 0
                ? totals.totalPnl.multiply(HUNDRED).divide(totals.capitalBase, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        AssetType filterType = EnumParser.parseNullable(AssetType.class, assetType, "enum.field.assetType");
        Set<String> liveKeys = dailyAggregationService.liveOpenAssetKeys(portfolioId, positions);
        DailyAgg daily = dailyAggregationService.aggregateDaily(portfolioId, filterType, totals.totalValue, liveKeys);
        BigDecimal dailyPnlAmount = daily.amount();
        BigDecimal dailyPnlPercent = computeDailyPercent(daily.priorBaseline(), dailyPnlAmount);
        // Lifecycle value = open MV + closed realised cash. Algebraic identity given the new
        // formula: totalPnl = (open MV + closed exit) − total entry, so open MV + closed exit
        // = totalPnl + totalEntry. We pass this to Real-return and MultiCurrency calculators
        // so they see the same lifecycle basis the headline PnL is computed against — without
        // it Reel% used open-only MV against a full CPI-deflated cost base and could exceed
        // nominal%, and currency frames recomputed PnL on open-only MV diverged from headline.
        BigDecimal lifecycleValue = totals.totalPnl.add(totals.totalEntry);
        // Realised cash from closed lots: spot realised (exit − entry) + VIOP realised (closed
        // exit − closed entry). Including VIOP brings the headline card in line with the
        // realised-PnL pie (AllocationCalculator.buildRealizedPnlAllocation) which sums both spot
        // and derivative closures. Earlier spot-only behaviour left a visible asymmetry: pie's
        // VIOP slice did not contribute to the card's "Realised" figure.
        BigDecimal viopClosedExit = noFilterOrViop(assetType)
                ? derivativeAggregationService.closedDerivativeExitMinusEntry(portfolioId) : BigDecimal.ZERO;
        BigDecimal realizedCashTry = spot.closedExitValue.subtract(spot.closedCost)
                .add(viopClosedExit)
                .setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        // Real return basis must include EVERY lot whose value is in lifecycleValue, otherwise
        // the % comes out wrong. Spot-only positions would exclude VIOP entries while
        // lifecycleValue still summed VIOP lifecycle value — producing real% > nominal% which
        // is impossible when CPI growth ≥ 0. We build a flat list of (entryDate, entryValue)
        // footprints from both PortfolioPosition (spot) AND DerivativePosition (VIOP) so the
        // deflated capital base mirrors the nominal capitalBase one-for-one.
        List<RealReturnCalculator.EntryFootprint> footprints = summaryFootprintBuilder.buildEntryFootprints(portfolioId, assetType, positions);
        // REAL (inflation-adjusted) return EXCLUDES VİOP — it is a spot purchasing-power metric (a leveraged
        // derivative book has no clean inflation-erodible invested capital; a net-0 hedge would otherwise show a
        // spurious real loss = CPI × gross notional). Deflate ONLY the spot lots against the SPOT lifecycle value;
        // a pure-VİOP portfolio then yields no REEL row, a mixed one reports REEL on its spot holdings. The FX
        // frame below still uses the full (spot + VİOP) footprints + total value — only the real metric is scoped.
        boolean realNoFilter = assetType == null || assetType.isBlank();
        BigDecimal spotLifecycleValue = realNoFilter
                ? spot.spotValue.add(spot.closedExitValue)
                : spot.spotValue;
        RealReturnCalculator.RealReturnSummary real = realReturnCalculator.computeFromFootprints(
                summaryFootprintBuilder.buildSpotEntryFootprints(assetType, positions), spotLifecycleValue);
        // Same footprints feed MultiCurrencyPnlCalculator so the USD/EUR frame's entry basis lines
        // up with the lifecycle numerator. Earlier spot-only positions produced an inflated frame %
        // for any portfolio holding VIOP (TRY-frame totalPnl also disagreed with the headline card).
        // Pass totals.totalValue (the actual mark-to-market NOTIONAL, open MV + closed exit), NOT
        // lifecycleValue (= totalPnl + totalEntry = EQUITY). They're equal for spot/LONG but differ for a
        // VIOP SHORT: equity already bakes in the direction, so combined with the frame's direction
        // correction it double-counted (a +$29 short read +$39). With notional, value − cost is the notional
        // change and the frame's per-VIOP correction flips the sign once → the right direction-aware K/Z.
        // Frame calculator still gets the NOTIONAL totalValue — it needs it to derive each currency's
        // direction-aware PnL (value − cost + correction), then restates the displayed value as cost + PnL.
        Map<String, com.finance.portfolio.dto.response.CurrencyFramePct> frames =
                multiCurrencyPnlCalculator.computeFromFootprints(footprints, totals.totalValue, dailyPnlAmount,
                        pnlPercent, dailyPnlPercent);
        // The frame's USD/EUR daily above is the TRY daily ÷ TODAY's single rate — fine for TRY-native assets, but
        // a USD/EUR-quoted VİOP's TRY daily mixes its native price move with the day's FX move, so single-rate
        // contaminates it (value-scaled). Replace the foreign daily with the per-date frame DELTA (today − prior
        // snapshot), which expresses each leg natively (own-currency day-move). TRY stays the stored daily.
        frames = frameOverrideService.withPerDateForeignDaily(portfolioId, filterType, frames);
        // Headline "Piyasa Değeri" (TRY) = EQUITY = cost + PnL = lifecycleValue, NOT the raw notional. A
        // profiting VIOP SHORT's notional falls below cost; equity rises (cost + profit), matching the K/Z
        // card and the per-currency frames (all value = cost + PnL now). Spot/LONG: lifecycleValue == notional.
        return responseMapper.toSummaryResponse(lifecycleValue, totals.totalEntry, totals.totalPnl,
                pnlPercent, dailyPnlAmount, dailyPnlPercent,
                real.realPnlTry(), real.realPnlPercent(), real.cpiGrowthPercent(),
                realizedCashTry, frames);
    }

    // Daily % = today's PnL over YESTERDAY's value. The prior baseline is summed per-asset (each row's
    // priorQty*priorPrice, recovered from its stored daily delta), NOT totalValue - dailyPnl — which on a
    // day a lot is added to an existing asset wrongly folds the new lot's full value into the baseline and
    // dilutes the %.
    private BigDecimal computeDailyPercent(BigDecimal priorBaseline, BigDecimal dailyPnl) {
        if (priorBaseline == null || dailyPnl == null || priorBaseline.signum() <= 0) return null;
        return dailyPnl.multiply(HUNDRED).divide(priorBaseline, MoneyScale.PRICE, RoundingMode.HALF_UP);
    }

    private SummaryTotals applyDerivativeAdjustment(Long portfolioId, String assetType, SpotTotals spot) {
        // Card must equal snapshot/chart and allocation pie sum per filter. To keep them in sync we
        // mirror exactly the snapshot's totals formula here:
        //   chart totalValue = open MV + closed exit cash   (per SnapshotTotals.toAggregateSnapshot)
        // where open MV for VIOP is the position EQUITY (entry notional + direction PnL), matching the
        // snapshot, so value − cost == direction PnL and a profiting SHORT raises the card (not lowers it).
        // Filtered Hisse/Kripto/Fon/Döviz: open spot MV only — matches getAssetTypePerformance and
        // the allocation pie which excludes the cash bucket for asset-type filters.
        boolean noFilter = assetType == null || assetType.isBlank();
        boolean viopOnly = !noFilter && AssetType.VIOP.name().equalsIgnoreCase(assetType);
        BigDecimal totalValue = noFilter
                ? spot.spotValue.add(spot.closedExitValue)
                : spot.spotValue;
        // "Tümü" is lifecycle (open MV + closed exit cash); a per-type filter is OPEN-ONLY (closed lots
        // live only in the Realized P/L section). Keep value, entry and PnL on the SAME basis so
        // value − entry = PnL and the per-currency frame (built from lifecycleValue = PnL + entry) equals
        // the TRY card — previously the filtered card mixed an open-only value with a lifecycle entry/PnL,
        // so USD/EUR (frame) showed the closed lot while TRY did not.
        BigDecimal totalEntry = noFilter ? spot.openCost.add(spot.closedCost) : spot.openCost;
        BigDecimal totalPnl = totalValue.subtract(totalEntry)
                .setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal capitalBase = totalEntry;
        if (!noFilter && !viopOnly) return new SummaryTotals(totalValue, totalEntry, totalPnl, capitalBase);
        DerivativeTotals deriv = derivativeAggregationService.openAndClosedDerivativeTotals(portfolioId);
        BigDecimal derivativePnl = deriv.openPnl.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal derivativeMv = deriv.openMarketValue.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal derivativeEntry = deriv.openEntryNotional.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal derivativeClosedExit = deriv.closedExitValue.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal derivativeClosedEntry = deriv.closedEntryNotional.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        if (viopOnly) {
            // VIOP filter card: open absolute MV only. Matches per-asset chart (which carries closed
            // lots as zero) and the allocation pie (closed VIOPs drop into cash bucket but no cash
            // bucket is emitted for an asset-type filter).
            return new SummaryTotals(derivativeMv, derivativeEntry, derivativePnl, derivativeEntry);
        }
        // Tümü: fold derivatives in just like SnapshotTotals does — open absolute MV adds to total,
        // closed exit cash (entry + realized) adds to total, PnL spans the lifecycle so it sums
        // open unrealized + (closed exit − closed entry).
        BigDecimal closedDerivativePnl = derivativeClosedExit.subtract(derivativeClosedEntry);
        return new SummaryTotals(
                totalValue.add(derivativeMv).add(derivativeClosedExit),
                totalEntry.add(derivativeEntry).add(derivativeClosedEntry),
                totalPnl.add(derivativePnl).add(closedDerivativePnl),
                capitalBase.add(derivativeEntry).add(derivativeClosedEntry));
    }

    private record SummaryTotals(BigDecimal totalValue, BigDecimal totalEntry,
                                  BigDecimal totalPnl, BigDecimal capitalBase) {}

    private boolean noFilterOrViop(String assetType) {
        return assetType == null || assetType.isBlank() || AssetType.VIOP.name().equalsIgnoreCase(assetType);
    }

    /**
     * Aggregates all lots of one asset (spot) into a single view: open quantity, weighted-average
     * entry, current price/market value, plus realized PnL from closed lots; empty figures when no lot exists.
     */
    @Transactional(readOnly = true)
    public AssetAggregateResponse getAssetAggregate(Long portfolioId, String assetTypeRaw, String assetCode) {
        return getAssetAggregate(portfolioId, assetTypeRaw, assetCode, null);
    }

    /**
     * Asset aggregate, optionally scoped to ONE derivative direction (LONG/SHORT) for VIOP — so the detail
     * page can show a same-symbol hedge's legs as separate, direction-aware K/Z cards instead of one netted
     * blend. {@code direction} is ignored for non-VIOP assets (spot has no direction dimension).
     */
    @Transactional(readOnly = true)
    public AssetAggregateResponse getAssetAggregate(Long portfolioId, String assetTypeRaw, String assetCode, String direction) {
        AssetType assetType = EnumParser.parseOrBadRequest(AssetType.class, assetTypeRaw, "enum.field.assetType");
        // VIOP lots live in DerivativePosition, not PortfolioPosition, so the spot path below always saw an
        // empty allLots for a future and returned frames = Map.of() — leaving the detail page to recompute
        // USD/EUR K/Z with a direction-blind frontend shortcut that disagreed with the summary card. Route
        // VIOP through the SAME MultiCurrencyPnlCalculator the summary uses so both read one direction-aware truth.
        if (assetType == AssetType.VIOP) {
            return viopAssetAggregateService.viopAssetAggregate(portfolioId, assetCode,
                    viopAssetAggregateService.parseDirectionOrNull(direction));
        }
        List<PortfolioPosition> allLots = positionRepository.findByPortfolioId(portfolioId).stream()
                .filter(p -> p.getAssetType() == assetType)
                .filter(p -> assetCode.equalsIgnoreCase(p.getAssetCode()))
                .toList();
        if (allLots.isEmpty()) {
            return new AssetAggregateResponse(
                    assetType.name(), assetCode, null, null,
                    0, BigDecimal.ZERO, null, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, java.util.Map.of());
        }
        List<PortfolioPosition> openLots = allLots.stream().filter(p -> !p.isClosed()).toList();
        PortfolioPosition first = allLots.get(0);
        AssetKey key = first.toAssetKey();
        // Value OPEN lots at the live market (selling) price via getBundle — the same field the position
        // table and the FX rate history use — not getExitBundle (buying). The exit/buying rate double-counts
        // the bid-ask spread once the detail page converts the TRY figure back to the asset's own currency,
        // which showed a USD holding as $0.9982 / $11,978 instead of $1.00 / $12.00. Closed lots still
        // realise at their own exit price below, so this only affects the open-lot current valuation.
        PriceBundle bundle = pricingPort.getBundle(key.type(), key.assetCode());
        BigDecimal currentPrice = bundle.price() != null ? bundle.price() : BigDecimal.ZERO;

        BigDecimal openQty = BigDecimal.ZERO;
        BigDecimal openEntryValue = BigDecimal.ZERO;
        BigDecimal openMarketValue = BigDecimal.ZERO;
        for (PortfolioPosition lot : openLots) {
            openQty = openQty.add(lot.getQuantity());
            openEntryValue = openEntryValue.add(lot.entryValue());
            openMarketValue = openMarketValue.add(lot.currentValue(currentPrice));
        }
        BigDecimal closedRealized = BigDecimal.ZERO;
        BigDecimal closedEntryValue = BigDecimal.ZERO;
        for (PortfolioPosition lot : allLots) {
            if (lot.isClosed() && lot.getExitPrice() != null) {
                closedRealized = closedRealized.add(
                        lot.getExitPrice().subtract(lot.getEntryPrice()).multiply(lot.getQuantity()));
                closedEntryValue = closedEntryValue.add(lot.entryValue());
            }
        }
        LocalDateTime earliest = null;
        for (PortfolioPosition lot : allLots) {
            if (earliest == null || (lot.getEntryDate() != null && lot.getEntryDate().isBefore(earliest))) {
                earliest = lot.getEntryDate();
            }
        }
        BigDecimal weightedAvg = openQty.signum() > 0
                ? openEntryValue.divide(openQty, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal unrealized = openMarketValue.subtract(openEntryValue);
        BigDecimal totalPnl = unrealized.add(closedRealized).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal pnlBasis = openEntryValue.add(closedEntryValue);
        BigDecimal pnlPercent = pnlBasis.signum() > 0
                ? totalPnl.multiply(new BigDecimal("100")).divide(pnlBasis, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        AssetMeta meta = bundle.meta() != null ? bundle.meta() : new AssetMeta(null, null);
        // Per-currency (USD/EUR) frames over ALL lots via the SAME primitive the summary + every chart point
        // use: each lot's cost at its entry-date FX, value at the point-date FX, closed lots locked at exit-date
        // FX. This is what lets the detail page show the asset's own-currency return instead of the TRY one
        // (a 12-USD holding reads ~0% in USD, not +6598%). lifecycleValue = open mark-to-market + closed
        // proceeds, so the TRY frame's value−cost reconciles with totalPnl above. Daily is null here — the
        // daily card is driven by the series' last point, not the aggregate.
        BigDecimal lifecycleValueTry = openMarketValue.add(closedEntryValue).add(closedRealized);
        java.util.Map<String, CurrencyFramePct> frames = multiCurrencyPnlCalculator.compute(
                allLots, lifecycleValueTry, null, pnlPercent, null);
        return new AssetAggregateResponse(
                assetType.name(), assetCode,
                meta.name(), meta.image(),
                openLots.size(), openQty, earliest, weightedAvg,
                currentPrice, openEntryValue, openMarketValue, totalPnl, pnlPercent, frames);
    }

    /** Allocation slices for the portfolio; {@code mode} selects the breakdown (asset-type vs realized-P/L) and {@code limit} caps the slices. */
    @Transactional(readOnly = true)
    public List<AllocationItem> getAllocation(Long portfolioId, String mode, String assetTypeFilter, Integer limit) {
        return allocationCalculator.compute(portfolioId, mode, assetTypeFilter, limit);
    }

}
