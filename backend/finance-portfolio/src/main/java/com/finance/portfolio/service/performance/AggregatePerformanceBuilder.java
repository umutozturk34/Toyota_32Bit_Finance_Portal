package com.finance.portfolio.service.performance;

import com.finance.portfolio.service.pricing.RealReturnCalculator;

import com.finance.portfolio.derivative.model.DerivativeDirection;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.dto.internal.PortfolioAggregateRow;
import com.finance.portfolio.dto.response.PerformanceAssetDetail;
import com.finance.portfolio.dto.response.PerformanceEvent;
import com.finance.portfolio.dto.response.PerformancePoint;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Builds the portfolio-aggregate and per-asset-type performance series — the most coupled part of the
 * performance engine. Reconstructs each chart point's direction-aware per-currency NOTIONAL from the
 * day's per-asset snapshots (the fix for the "Tümü-broken-but-VİOP-filter-correct" bug, where a date-blind
 * notional read a USD SHORT's profit as a loss), pairs it with the FX-frame calculator, and folds in trade
 * events + realized cash. Runs inside the caller's read-only transaction.
 */
@Component
@RequiredArgsConstructor
class AggregatePerformanceBuilder {

    private static final List<String> FRAME_CCYS = List.of("USD", "EUR");

    private final PortfolioDailySnapshotRepository dailySnapshotRepository;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final PortfolioPositionRepository positionRepository;
    private final DerivativePositionRepository derivativePositionRepository;
    private final PerformanceEntryFootprintBuilder footprintBuilder;
    private final PerCurrencyFrameCalculator frameCalculator;
    private final PerformanceAggregationHelper aggregationHelper;
    private final PerformanceEventAssembler eventAssembler;
    private final CashRealisedSeriesBuilder cashSeriesBuilder;

    List<PerformancePoint> getAggregatePerformance(Long portfolioId, LocalDateTime start, LocalDateTime end) {
        List<PortfolioAggregateRow> aggregates = dailySnapshotRepository
                .findAggregateByPortfolio(portfolioId, start, end);
        List<PortfolioAssetDailySnapshot> assetSnapshots = assetSnapshotRepository
                .findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(portfolioId, start, end);
        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(portfolioId);
        List<DerivativePosition> derivatives = derivativePositionRepository.findByPortfolioId(portfolioId);

        Map<LocalDateTime, List<PortfolioAssetDailySnapshot>> assetsByCreatedAt = assetSnapshots.stream()
                .collect(Collectors.groupingBy(PortfolioAssetDailySnapshot::getCreatedAt,
                        LinkedHashMap::new, Collectors.toList()));

        List<RealReturnCalculator.EntryFootprint> fps = footprintBuilder.footprints(positions, derivatives);
        Map<String, TreeMap<LocalDate, BigDecimal>> fxByCcy = frameCalculator.fxSeriesByCcy(fps, end.toLocalDate());

        List<PerformancePoint> result = new ArrayList<>();
        LocalDateTime prevTime = start;

        // Carry-forward per-asset state (mirror getAssetTypePerformance): an asset row exists only at the
        // timestamp its value changed, and a single position's two opposite lots (a LONG+SHORT hedge on one
        // symbol) can land at different instants. Matching only the rows at agg.createdAt() left lots out, so
        // the per-currency NOTIONAL was partial → the USD/EUR value read half/0 on the lots' shared days while
        // TRY (agg.totalValueTry, always full) stayed correct — the "Tümü-broken-but-VİOP-filter-correct" bug.
        // Folding the latest rows per code up to each aggregate timestamp gives perCcyInputs the COMPLETE state.
        List<Map.Entry<LocalDateTime, List<PortfolioAssetDailySnapshot>>> assetGroups =
                new ArrayList<>(assetsByCreatedAt.entrySet());
        Map<String, List<PortfolioAssetDailySnapshot>> latestByAsset = new LinkedHashMap<>();
        int assetIdx = 0;

        for (PortfolioAggregateRow agg : aggregates) {
            List<PortfolioAssetDailySnapshot> anonymous = new ArrayList<>();
            while (assetIdx < assetGroups.size()
                    && !assetGroups.get(assetIdx).getKey().isAfter(agg.createdAt())) {
                Map<String, List<PortfolioAssetDailySnapshot>> updatesByCode = new LinkedHashMap<>();
                for (PortfolioAssetDailySnapshot snap : assetGroups.get(assetIdx).getValue()) {
                    if (snap.getAssetCode() == null) anonymous.add(snap);
                    else updatesByCode.computeIfAbsent(snap.getAssetCode(), k -> new ArrayList<>()).add(snap);
                }
                latestByAsset.putAll(updatesByCode);
                assetIdx++;
            }
            List<PortfolioAssetDailySnapshot> assets = new ArrayList<>(anonymous);
            latestByAsset.values().forEach(assets::addAll);
            Map<String, BigDecimal> currTypeValues = new LinkedHashMap<>();
            // ONE per-date footprint source for BOTH the per-type detail (K/Z Katkısı) and the aggregate frame,
            // so the VİOP contribution uses the snapshot's per-date notional (direction-aware) instead of the
            // date-blind/stale footprintsByType (which read a USD-quoted open leg's profit as a loss).
            PerCcyInputs pc = perCcyInputs(positions, derivatives, assets, agg.createdAt().toLocalDate());
            // Detail (K/Z Katkısı) must drop a SPOT closed AS OF this date — otherwise its lingering close-day row
            // shows as a live contributor beside the closed bucket (the per-type view and perCcyInputs already do
            // this). VIOP rows are kept here; their value-less zero rows are filtered inside aggregateByType.
            Set<String> closedAsOf = closedAsOfCodes(positions, agg.createdAt().toLocalDate());
            List<PortfolioAssetDailySnapshot> detailAssets = closedAsOf.isEmpty() ? assets : assets.stream()
                    .filter(a -> a.getAssetType() == AssetType.VIOP
                            || a.getAssetCode() == null
                            || !closedAsOf.contains(a.getAssetCode()))
                    .toList();
            List<PerformanceAssetDetail> details = detailsWithFrames(
                    aggregationHelper.aggregateByType(detailAssets, currTypeValues),
                    agg.createdAt().toLocalDate(), pc.fpsByType(), fxByCcy);
            List<PerformanceEvent> events = eventAssembler.buildEvents(positions, derivatives, prevTime, agg.createdAt());
            // Foreign (USD/EUR) frame value leg = direction-blind NOTIONAL (not the equity agg.totalValueTry),
            // paired with the direction-aware footprints — so pointFrame's open/closed VIOP correction flips a
            // SHORT's sign without double-counting (equity + correction over-counts; the summary card / per-type
            // path both feed notional for the same reason). TRY display value stays the equity total. When the
            // timestamp has no per-asset rows we cannot rebuild the notional, so fall back to the equity total.
            FrameMapsR frames = assets.isEmpty()
                    ? frameCalculator.framesForTotal(agg.createdAt().toLocalDate(), agg.totalValueTry(), fps, fxByCcy)
                    : frameCalculator.framesForTotal(agg.createdAt().toLocalDate(), pc.notionalTry(), pc.fps(), fxByCcy);
            result.add(new PerformancePoint(agg.createdAt(), agg.totalValueTry(), agg.cashTry(),
                    agg.totalPnlTry(), agg.pnlPercent(), details, events,
                    frames.cost(), frames.value(), frames.realized(), frames.pnl()));
            prevTime = agg.createdAt();
        }
        return result;
    }

    /** Per-currency frame inputs for ONE chart point: the NOTIONAL value (Σ per-asset market value at this date)
     *  plus direction-aware footprints (open VIOP at the date's notional, closed VIOP carrying proceeds + sign).
     *  framesForTotal over these yields the SAME per-date, direction-aware USD/EUR K/Z the card shows —
     *  cost@entry-FX, value@point-FX, SHORT flipped — instead of converting a TRY equity at the point FX (which
     *  re-prices the cost leg at today's rate and reads a SHORT's profit as a loss). TRY stays on the equity snapshot. */
    PerCcyInputs perCcyInputs(List<PortfolioPosition> positions, List<DerivativePosition> derivatives,
                              List<PortfolioAssetDailySnapshot> dayAssets, LocalDate date) {
        // notionalTotal MUST match the footprints' value legs exactly (open at notional, closed at proceeds) so
        // framesForTotal doesn't drop the value when a lot is sold: open spot → its market value (rows), closed
        // spot/VIOP → proceeds (entry + realized), open VIOP → the date's notional. Summing raw per-asset rows
        // instead double-counts a closing lot AND omits its proceeds → the chart halved on sell.
        BigDecimal notional = BigDecimal.ZERO;
        Map<String, BigDecimal> unitPrice = new LinkedHashMap<>();
        // DATE-AWARE close-symbol exclusion (mirrors getAssetTypePerformance's open-leg filter). A spot symbol
        // CLOSED AS OF this date still leaves a close-day row in dayAssets (marketValueTry == its exit proceeds);
        // summing that row into the notional here AND adding the same position's exitValue as closed proceeds
        // below (line ~157) counted the sold lot's value TWICE, doubling the USD/EUR frame on the sell day. The
        // closed proceeds still reach the frame via the position footprint, so excluding the row drops the
        // duplicate, not the value.
        Set<String> closedAsOf = closedAsOfCodes(positions, date);
        for (PortfolioAssetDailySnapshot a : dayAssets) {
            if (a.getAssetType() == AssetType.VIOP) {
                if (a.getAssetCode() != null && a.getUnitPriceTry() != null) {
                    unitPrice.putIfAbsent(a.getAssetCode(), a.getUnitPriceTry());
                }
            } else if (a.getMarketValueTry() != null
                    && (a.getAssetCode() == null || !closedAsOf.contains(a.getAssetCode()))) {
                notional = notional.add(a.getMarketValueTry());   // open spot at its market value
            }
        }
        List<RealReturnCalculator.EntryFootprint> fps = new ArrayList<>();
        // Same footprints grouped by asset type, so the per-TYPE detail frame (K/Z Katkısı) uses the SAME
        // per-date direction-aware notionals as the aggregate — NOT the date-blind footprintsByType, whose
        // openDerivativeNotionalTry returns a USD-quoted contract's STALE entry notional (no FX) and made the
        // VİOP contribution read a profiting position as a loss.
        Map<String, List<RealReturnCalculator.EntryFootprint>> fpsByType = new LinkedHashMap<>();
        for (PortfolioPosition p : positions) {
            if (p.getEntryDate() == null || p.entryValue() == null) continue;
            LocalDate exit = p.getExitDate() != null ? p.getExitDate().toLocalDate() : null;
            BigDecimal exitValue = (exit != null && p.realizedPnl() != null) ? p.realizedPnl().add(p.entryValue()) : null;
            if (exit != null && !exit.isAfter(date) && exitValue != null) notional = notional.add(exitValue); // closed spot proceeds
            RealReturnCalculator.EntryFootprint fp =
                    new RealReturnCalculator.EntryFootprint(p.getEntryDate().toLocalDate(), p.entryValue(), exit, exitValue);
            fps.add(fp);
            if (p.getAssetType() != null) fpsByType.computeIfAbsent(p.getAssetType().name(), k -> new ArrayList<>()).add(fp);
        }
        for (DerivativePosition d : derivatives) {
            BigDecimal entry = d.nominalExposure();
            if (d.getEntryDate() == null || entry == null || d.getViopContract() == null) continue;
            if (d.getEntryDate().isAfter(date)) continue;
            int sign = d.getDirection() == DerivativeDirection.SHORT ? -1 : 1;
            RealReturnCalculator.EntryFootprint fp;
            if (d.getCloseDate() != null && !d.getCloseDate().isAfter(date)) {
                BigDecimal r = d.realizedOrUnrealizedPnl(d.getClosePrice());
                BigDecimal proceeds = r != null ? r.add(entry.abs()) : entry.abs();
                notional = notional.add(proceeds);   // closed VIOP proceeds
                fp = RealReturnCalculator.EntryFootprint.viopClosed(d.getEntryDate(), entry.abs(),
                        d.getCloseDate(), proceeds, d.notionalAt(d.getClosePrice()), sign);
            } else {
                // OPEN AS OF this date: closeDate == null OR closeDate is AFTER date (a since-closed lot that was
                // still live on this point). The old `else if (closeDate == null)` dropped the latter entirely, so a
                // closed lot contributed 0 notional on every pre-close day → the aggregate USD value read $0 and
                // ramped only once the close date was reached (the "Tümü broken, VİOP-filter correct" bug).
                BigDecimal price = unitPrice.get(d.getViopContract().getSymbol());
                BigDecimal cn = price != null ? d.notionalAt(price) : entry.abs();
                notional = notional.add(cn);          // open VIOP at the date's notional
                fp = RealReturnCalculator.EntryFootprint.viopOpen(d.getEntryDate(), entry.abs(), sign, cn);
            }
            fps.add(fp);
            fpsByType.computeIfAbsent("VIOP", k -> new ArrayList<>()).add(fp);
        }
        return new PerCcyInputs(notional, fps, fpsByType);
    }

    /**
     * Asset codes CLOSED AS OF {@code date}: present in {@code positions} but with no lot still open that date
     * (every lot's exit ≤ date). Their lingering close-day rows (marketValue == exit) must be excluded from the
     * open-value / contributor aggregation so a sold-as-of-date symbol is not counted as a live holding — its
     * proceeds are booked once via the position footprint / addClosedEquity. A code with a still-open lot, or one
     * absent from positions entirely (snapshots-only), is NOT returned (kept). Shared by perCcyInputs (USD/EUR
     * notional), getAssetTypePerformance (TRY open leg + detail) and getAggregatePerformance ("Tümü" detail).
     */
    private static Set<String> closedAsOfCodes(List<PortfolioPosition> positions, LocalDate date) {
        Set<String> openAsOf = positions.stream()
                .filter(p -> p.getEntryDate() != null && !p.getEntryDate().toLocalDate().isAfter(date))
                .filter(p -> !p.isClosed() || p.getExitDate().toLocalDate().isAfter(date))
                .map(PortfolioPosition::getAssetCode)
                .collect(Collectors.toSet());
        return positions.stream()
                .map(PortfolioPosition::getAssetCode)
                .filter(code -> code != null && !openAsOf.contains(code))
                .collect(Collectors.toSet());
    }

    List<PerformancePoint> getAssetTypePerformance(Long portfolioId, AssetType assetType,
                                                   LocalDateTime start, LocalDateTime end) {
        List<PortfolioAssetDailySnapshot> snapshots = assetSnapshotRepository
                .findByPortfolioIdAndAssetTypeAndCreatedAtBetweenOrderByCreatedAtAsc(
                        portfolioId, assetType, start, end);
        List<PortfolioPosition> positions = assetType == AssetType.VIOP
                ? List.of()
                : positionRepository.findByPortfolioId(portfolioId).stream()
                        .filter(p -> p.getAssetType() == assetType)
                        .toList();
        List<DerivativePosition> derivatives = assetType == AssetType.VIOP
                ? derivativePositionRepository.findByPortfolioId(portfolioId)
                : List.of();

        Map<LocalDateTime, List<PortfolioAssetDailySnapshot>> grouped = snapshots.stream()
                .collect(Collectors.groupingBy(PortfolioAssetDailySnapshot::getCreatedAt,
                        LinkedHashMap::new, Collectors.toList()));

        List<RealReturnCalculator.EntryFootprint> fps = footprintBuilder.footprints(positions, derivatives);
        Map<String, TreeMap<LocalDate, BigDecimal>> fxByCcy = frameCalculator.fxSeriesByCcy(fps, end.toLocalDate());

        List<PerformancePoint> result = new ArrayList<>();
        LocalDateTime prevTime = start;

        // Carry-forward per-asset state across timestamps. Asset snapshots are sparse: a row only
        // exists at the createdAt where that asset's value changed (e.g. close-day zero marker at
        // 00:00:01 for TUPRS while other stocks stayed at 00:00:00). Aggregating only the rows
        // present at the current timestamp would drop every other asset to 0 for that instant,
        // producing the chart's "single asset crashes the whole filter view" artefact. The fold
        // here keeps the latest known snapshot list per asset code (list, not single, because
        // per-lot snapshots can co-exist at the same timestamp — aggregateByCode sums them) and
        // re-aggregates the carried state at every timestamp.
        Map<String, List<PortfolioAssetDailySnapshot>> latestByAsset = new LinkedHashMap<>();

        for (Map.Entry<LocalDateTime, List<PortfolioAssetDailySnapshot>> e : grouped.entrySet()) {
            // Carry-forward keyed by assetCode; rows without an assetCode (anonymous derivative-style
            // snapshots) can't be folded across time so they only contribute at their own timestamp.
            List<PortfolioAssetDailySnapshot> anonymous = new ArrayList<>();
            Map<String, List<PortfolioAssetDailySnapshot>> updatesByCode = new LinkedHashMap<>();
            for (PortfolioAssetDailySnapshot snap : e.getValue()) {
                if (snap.getAssetCode() == null) anonymous.add(snap);
                else updatesByCode.computeIfAbsent(snap.getAssetCode(), k -> new ArrayList<>()).add(snap);
            }
            latestByAsset.putAll(updatesByCode);
            List<PortfolioAssetDailySnapshot> carriedState = new ArrayList<>(anonymous);
            latestByAsset.values().forEach(carriedState::addAll);
            Map<String, BigDecimal> currAssetValues = new LinkedHashMap<>();
            PerformanceAggregationHelper.AssetCodeAgg agg = aggregationHelper.aggregateByCode(carriedState, currAssetValues);
            LocalDate snapDate = e.getKey().toLocalDate();
            // OPEN leg (TRY value + open PnL) excludes rows of symbols CLOSED AS OF this date. Such a symbol still
            // leaves a row in carriedState (a carried-forward open row, or the close-day row) with marketValue=exit
            // and pnl=realized, and realizedFor books that same realized in the CLOSED leg for this date — counting
            // it here too makes the TRY Total = openPnl + realized read 2× (the 0.70 → 1.40 doubling). The exclusion
            // is DATE-AWARE: a symbol still OPEN on a historical date keeps its value/PnL there, so the line shows
            // the week of history and only drops on/after the close date — NOT a flat 0 across the whole range. A
            // symbol absent from positions (snapshots-only) is kept. Mirrors framesFor's open-footprint filter for
            // USD/EUR. VIOP keeps its own equity handling (closed-on-day lots already leave its aggregate).
            PerformanceAggregationHelper.AssetCodeAgg openAgg = agg;
            if (assetType != AssetType.VIOP) {
                Set<String> closedAsOf = closedAsOfCodes(positions, snapDate);
                if (!closedAsOf.isEmpty()) {
                    List<PortfolioAssetDailySnapshot> openRows = carriedState.stream()
                            .filter(s -> s.getAssetCode() == null || !closedAsOf.contains(s.getAssetCode()))
                            .toList();
                    openAgg = aggregationHelper.aggregateByCode(openRows, new LinkedHashMap<>());
                }
            }
            List<PerformanceEvent> events = eventAssembler.buildEvents(positions, derivatives, prevTime, e.getKey());
            // The filtered VALUE stays HELD (open) market value — the card and value line drop when a position is
            // sold. The P&L breakdown FOLDS the type's CLOSED lots so the filtered Kâr/Zarar chart keeps its
            // Total/Open/Closed lines (the closed line was previously flat 0 under any filter). combineTypeFrames
            // adds the closed realized + closed cost (USD/EUR frames) on top of the open frame; realizedFor gives
            // the TRY realized scalar. Crucially this avoids the ~2x double-count that bit fully-closed derivatives
            // (whose asset snapshot still carries the exit value): value stays the open HELD value, not held+proceeds.
            // VIOP held value = EQUITY (cost + direction-aware PnL): RISES as a SHORT profits and DROPS when a lot
            // is sold (one less open lot). Spot keeps its market value.
            BigDecimal heldValue = assetType == AssetType.VIOP
                    ? agg.totalCost().add(agg.totalPnl())
                    : openAgg.totalValue();
            // Open frame: OPEN notional (agg.totalValue → drops on sell) + DIRECTION footprints (viopOpen at the
            // date's notional) so the per-currency open value/PnL is per-date direction-aware (SHORT → +$29, not
            // the FX-drifted −$18). Closed lots flow through the closed frame below (realized line), untouched.
            PerCcyInputs pc = perCcyInputs(positions, derivatives, carriedState, snapDate);
            FrameMaps open = frameCalculator.framesFor(snapDate, agg.totalValue(), pc.fps(), fxByCcy);
            List<RealReturnCalculator.EntryFootprint> closedFps = pc.fps().stream()
                    .filter(f -> f.exitDate() != null && !f.exitDate().isAfter(snapDate)).toList();
            FrameMapsR closed = frameCalculator.framesForTotal(snapDate, cashSeriesBuilder.sumClosedExit(closedFps, snapDate), closedFps, fxByCcy);
            TypeFrames tf = combineTypeFrames(open, closed);
            RealizedToDate rz = cashSeriesBuilder.realizedFor(positions, derivatives, snapDate);
            // Detail list (K/Z Katkısı contributors) must mirror the OPEN leg (openAgg), not the unfiltered agg:
            // a symbol closed AS OF this date lingers in carriedState with a zero/exit row and leaked as a "+0"
            // ghost contributor beside its real realized P&L in the __closed__ bucket. openAgg already drops
            // closed-as-of-date symbols (VIOP: openAgg == agg, so byte-identical there).
            List<PerformanceAssetDetail> detailed = detailsWithFrames(
                    openAgg.details(), snapDate, Map.of(assetType.name(), pc.fps()), fxByCcy);
            result.add(assembleTypePoint(e.getKey(), heldValue, heldValue, openAgg.totalPnl(),
                    rz.realized(), rz.closedCost(), detailed, events,
                    tf.cost(), tf.value(), tf.realized(), tf.pnl()));
            prevTime = e.getKey();
        }
        return result;
    }

    /**
     * Combine a type's OPEN frame (held value + open cost) with its CLOSED frame (realized + closed cost) per
     * currency, avoiding the double-count that bit fully-closed derivatives (whose asset snapshot keeps the
     * exit value, so adding the closed proceeds again inflated PnL ~2×). value = held; cost = open + closed;
     * realized = closed realized; pnl = open(value − cost) + realized. A type with only closed lots has no open
     * cost, so its open PnL is 0 and the total equals the realized exactly.
     */
    private TypeFrames combineTypeFrames(FrameMaps open, FrameMapsR closed) {
        Map<String, BigDecimal> value = new LinkedHashMap<>();
        Map<String, BigDecimal> cost = new LinkedHashMap<>();
        Map<String, BigDecimal> realized = new LinkedHashMap<>();
        Map<String, BigDecimal> pnl = new LinkedHashMap<>();
        for (String c : FRAME_CCYS) {
            BigDecimal ov = open.value().get(c);
            BigDecimal oc = open.cost().get(c);
            BigDecimal rz = closed.realized().get(c);
            BigDecimal cc = closed.cost().get(c);
            if (ov != null) value.put(c, ov);
            if (rz != null) realized.put(c, rz);
            if (oc != null || cc != null) {
                cost.put(c, (oc != null ? oc : BigDecimal.ZERO).add(cc != null ? cc : BigDecimal.ZERO));
            }
            BigDecimal openPnl = (ov != null && oc != null) ? ov.subtract(oc) : BigDecimal.ZERO;
            BigDecimal total = openPnl.add(rz != null ? rz : BigDecimal.ZERO);
            if (ov != null || rz != null) pnl.put(c, total);
        }
        return new TypeFrames(value, cost, realized, pnl);
    }

    /** Attach per-type entry-FX cost/value frames to each detail row at the point date. */
    private List<PerformanceAssetDetail> detailsWithFrames(List<PerformanceAssetDetail> details, LocalDate date,
            Map<String, List<RealReturnCalculator.EntryFootprint>> fpsByType,
            Map<String, TreeMap<LocalDate, BigDecimal>> fxByCcy) {
        List<PerformanceAssetDetail> out = new ArrayList<>(details.size());
        for (PerformanceAssetDetail d : details) {
            List<RealReturnCalculator.EntryFootprint> fps = fpsByType.getOrDefault(d.assetType(), List.of());
            FrameMaps frames = frameCalculator.framesFor(date, d.valueTry(), fps, fxByCcy);
            // Per-currency PnL = value − cost. framesFor's value already folds the open-VIOP directionAdjustment
            // into EQUITY, so value − cost is the DIRECTION-AWARE PnL (a profiting SHORT reads +, a hedge nets) —
            // the K/Z-contribution breakdown reads this instead of converting the TRY pnlTry at today's FX.
            Map<String, BigDecimal> pnl = new LinkedHashMap<>();
            for (Map.Entry<String, BigDecimal> e : frames.value().entrySet()) {
                BigDecimal c = frames.cost().get(e.getKey());
                if (c != null && e.getValue() != null) pnl.put(e.getKey(), e.getValue().subtract(c));
            }
            out.add(d.withFrames(frames.cost(), frames.value(), pnl));
        }
        return out;
    }

    /**
     * Builds one asset-type-filtered performance point. {@code displayValue} stays the type's HELD market
     * value so the value chart drops when a position is sold (fully or partially) — selling reduces the
     * holdings shown under that filter. The P&L breakdown still folds the type's CLOSED positions in, so a
     * Kâr/Zarar chart keeps its Total/Open/Closed lines: realized = cumulative realized; total PnL = open
     * unrealized + realized; percent = total PnL over (open + closed) cost.
     */
    static PerformancePoint assembleTypePoint(LocalDateTime timestamp, BigDecimal displayValue, BigDecimal openMv,
                                                      BigDecimal openPnl, BigDecimal realized, BigDecimal closedCost,
                                                      List<PerformanceAssetDetail> details, List<PerformanceEvent> events,
                                                      Map<String, BigDecimal> costByCcy, Map<String, BigDecimal> valueByCcy,
                                                      Map<String, BigDecimal> realizedByCcy, Map<String, BigDecimal> pnlByCcy) {
        BigDecimal totalPnl = openPnl.add(realized);
        BigDecimal typeCost = openMv.subtract(openPnl).add(closedCost);
        BigDecimal pnlPercent = typeCost.signum() > 0
                ? totalPnl.multiply(new BigDecimal("100")).divide(typeCost, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        // displayValue stays the HELD market value (drops when sold); pnlByCcy carries the total (open +
        // realized) per currency so the USD/EUR headline is right without re-pricing closed proceeds at today.
        return new PerformancePoint(timestamp, displayValue, realized, totalPnl, pnlPercent, details, events,
                costByCcy, valueByCcy, realizedByCcy, pnlByCcy);
    }
}
