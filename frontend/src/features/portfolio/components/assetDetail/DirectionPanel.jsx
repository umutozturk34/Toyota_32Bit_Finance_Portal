import { useMemo } from 'react';
import { motion } from 'framer-motion';
import { TrendingUp, TrendingDown } from '../../../../shared/components/feedback/AnimatedIcons';
import { useAssetSeries, useAssetAggregate } from '../../hooks/usePortfolioData';
import { formatPercentSmart, changeColors, changeBg, getChangeClass } from '../../../../shared/utils/formatters';
import { cardVariants } from '../../../../shared/utils/animations';
import RangeSelector from '../../../../shared/components/form/RangeSelector';
import Card from '../../../../shared/components/card';
import Spinner from '../../../../shared/components/feedback/Spinner';
import FitMoney from '../../../../shared/components/FitMoney';
import { computeViopAggregate, computeClosedAggregate } from '../../lib/assetAggregates';
import { processAssetSeries } from '../../lib/assetSeriesProcessing';
import { LotsTable } from '../LotsTable';
import { STAT_CARD_DEFS, lotDirection } from '../../lib/assetDetailHelpers';
import { AssetChart } from './AssetChart';

/**
 * One position view — stat cards + K/Z + daily + chart + lots — for either the whole symbol ({@code direction}
 * null) or a single VİOP leg (LONG/SHORT). A same-symbol hedge renders two of these so each leg shows its own
 * direction-aware K/Z and chart instead of one netted spot-like blend; the per-direction backend aggregate +
 * series (fetched with {@code direction}) keep each leg's USD/EUR framing correct.
 */
export function DirectionPanel({
  portfolioId, asset, lots, direction,
  t, isDark, money, bigMoney, nativeCurrency, convertAt, convertBetween, displayCurrency, frame,
  range, setRange, isUpdating, onEditLot, onSellLot, onReopenLot, onDeleteLot,
}) {
  const { data: rawSeries = [], isLoading: loading } = useAssetSeries(
    portfolioId, asset.assetType, asset.assetCode, range, direction
  );
  const series = useMemo(() => processAssetSeries(rawSeries, lots), [rawSeries, lots]);

  // VİOP now flows through the SAME backend aggregate (getAssetAggregate → MultiCurrencyPnlCalculator) the summary
  // card uses, so its USD/EUR K/Z matches the summary instead of a frontend shortcut. The direction param scopes a
  // hedge's leg to its own aggregate. (Previously VİOP passed null → empty backend frame → direction-blind headline.)
  const { data: aggregate } = useAssetAggregate(
    portfolioId, asset.assetType, asset.assetCode, direction
  );

  const viopAggregate = useMemo(() => computeViopAggregate(asset.assetType, lots), [asset.assetType, lots]);
  const closedAggregate = useMemo(() => computeClosedAggregate(lots), [lots]);

  const effectiveAggregate = (aggregate && aggregate.lotCount > 0 ? aggregate : viopAggregate) || closedAggregate;
  const hasAggregate = effectiveAggregate && effectiveAggregate.lotCount > 0;
  const aggQuantity = hasAggregate ? effectiveAggregate.totalQuantity : asset.quantity;
  const aggEntryDate = hasAggregate ? effectiveAggregate.earliestEntryDate : asset.entryDate;
  const aggEntryPriceTry = hasAggregate ? effectiveAggregate.weightedAvgEntryPrice : asset.entryPrice;
  const aggCurrentPriceTry = hasAggregate ? effectiveAggregate.currentPriceTry : asset.currentPriceTry;
  const aggMarketValueTry = hasAggregate ? effectiveAggregate.totalMarketValueTry : asset.marketValueTry;
  const aggPnlTry = hasAggregate ? effectiveAggregate.totalPnlTry : asset.pnlTry;
  const aggPnlPercent = hasAggregate ? effectiveAggregate.pnlPercent : asset.pnlPercent;
  const isNonTryFrame = displayCurrency === 'USD' || displayCurrency === 'EUR';
  // Backend per-lot frame (spot, lot-accurate) from the RAW aggregate — NOT effectiveAggregate, which swaps
  // to the frames-less closed/viop fallback. VIOP/derivatives ship no frames map; the headline frame for
  // those is derived below via the universal frame() so it matches the per-lot rows instead of showing the
  // TRY % beside a € / $ label.
  const backendFrame = isNonTryFrame ? aggregate?.frames?.[displayCurrency] : null;

  const entryDateIso = aggEntryDate ? new Date(aggEntryDate).toLocaleDateString('sv-SE') : null;
  const todayIso = new Date().toLocaleDateString('sv-SE');
  const targetCurrency = displayCurrency === 'ORIGINAL' || !displayCurrency ? nativeCurrency : displayCurrency;
  // Blended entry price must convert each lot at its OWN entry date — a single-date FX conversion
  // of the TRY weighted-average misprices multi-lot non-TRY assets whose lots span different dates.
  const entryPriceConverted = useMemo(() => {
    let nativeEntryValue = 0;
    let nativeQty = 0;
    for (const lot of lots) {
      const price = Number(lot.entryPrice);
      const qty = Number(lot.quantity);
      if (!Number.isFinite(price) || !Number.isFinite(qty) || qty <= 0 || !lot.entryDate) continue;
      const native = convertAt(price, 'TRY', String(lot.entryDate).slice(0, 10), nativeCurrency);
      if (native == null) continue;
      nativeEntryValue += native * qty;
      nativeQty += qty;
    }
    if (nativeQty > 0) return nativeEntryValue / nativeQty;
    return entryDateIso ? convertAt(aggEntryPriceTry, 'TRY', entryDateIso, nativeCurrency) : null;
  }, [lots, convertAt, nativeCurrency, entryDateIso, aggEntryPriceTry]);
  const currentPriceConverted = convertAt(aggCurrentPriceTry, 'TRY', todayIso, nativeCurrency);
  const marketValueConverted = convertAt(aggMarketValueTry, 'TRY', todayIso, nativeCurrency);
  const aggAsset = { ...asset, quantity: aggQuantity, entryDate: aggEntryDate };
  // Values are already converted to targetCurrency; FitMoney shows them full-when-fits, compact-when-too-wide
  // (never clipping digits), with the exact figure always in title= — so a huge market value can never spill
  // outside its stat card. base=targetCurrency means convert() is a no-op (from===target), no re-conversion.
  const statRawFor = (key) => {
    if (key === 'entryPrice') return entryPriceConverted;
    if (key === 'currentPriceTry') return currentPriceConverted;
    if (key === 'marketValueTry') return marketValueDisplay;
    return null;
  };

  // A fully-closed position's aggregate P&L is exit-frozen TRY; converting it at today's spot diverges
  // from the per-lot rows (which convert at each exit date). Mirror the closed-lot convention and use
  // the position's exit date (latest lot exit) as the FX date for the aggregate amount.
  const isFullyClosed = lots.length > 0 && lots.every((l) => l.exitDate != null);
  const aggExitDateIso = isFullyClosed
    ? lots.reduce((latest, l) => {
        const iso = l.exitDate ? new Date(l.exitDate).toLocaleDateString('sv-SE') : null;
        return iso && (latest == null || iso > latest) ? iso : latest;
      }, null)
    : null;
  const pnlFxDate = aggExitDateIso ?? todayIso;
  // directionSign for the headline: −1 for a VIOP SHORT, +1 otherwise. The frame's value − cost is backwards
  // for a SHORT (its converted notional falls as it profits); the sign flips its USD/EUR K/Z.
  const isViop = asset.assetType === 'VIOP';
  // Prefer the explicit panel direction (a split leg), else the lots' own direction, else the asset prefix.
  const viopDirection = isViop
    ? (direction || lotDirection(lots[0]) || asset.derivative?.direction || String(asset.assetName || '').split(' · ')[0])
    : null;
  const headDirectionSign = viopDirection === 'SHORT' ? -1 : 1;
  // A VIOP's entry cost ≠ value − pnl: pnl is direction-aware, so a SHORT's value − pnl mis-derives the cost.
  // Sum the backend entryValueTry over the lots the aggMarketValueTry (= notional) covers — open lots while the
  // position is live (computeViopAggregate), all lots once fully closed (computeClosedAggregate now surfaces the
  // close-frozen notional, not 0) — so directionSign × (value − cost) yields the direction-aware K/Z for both.
  // Non-VIOP keeps the value − pnl basis, exact for LONG/spot where pnl == value − cost.
  const lotEntryValueTry = (l) => (l.entryValueTry != null
    ? Number(l.entryValueTry)
    : (Number(l.entryPrice) * Number(l.quantity) || 0));
  const useViopEntryCost = isViop;
  const headCostTry = useViopEntryCost
    ? lots.filter((l) => isFullyClosed || l.exitDate == null).reduce((sum, l) => sum + lotEntryValueTry(l), 0)
    : (Number(aggMarketValueTry) || 0) - (Number(aggPnlTry) || 0);
  // directionSign flips the frame only when the cost is a true entry basis (any VIOP, open or closed). The
  // non-VIOP fallback derives cost as value − pnl, where value − cost == pnl already carries the right sign,
  // so applying directionSign there would double-flip — keep it +1 for that branch.
  const headFrameSign = useViopEntryCost ? headDirectionSign : 1;
  // Headline K/Z in the display-currency frame — ONE rule for every asset type. Prefer the backend per-lot
  // frame (spot, lot-accurate); when it is absent (VIOP/derivatives ship no frames map) derive it from the
  // aggregate totals via the SAME universal frame() the per-lot rows + daily card use, so the headline
  // matches the lots instead of a TRY % beside €.
  const headFrame = (backendFrame && backendFrame.totalPnl != null)
    ? { pnl: Number(backendFrame.totalPnl), pnlPercent: backendFrame.pnlPercent, base: displayCurrency }
    : isViop
      // A VIOP aggregate's K/Z is the SUM of its lots' DIRECTION-AWARE pnlTry (computeViopAggregate /
      // computeClosedAggregate), already net for a LONG+SHORT hedge. A single headDirectionSign over the blended
      // notional cannot net a mixed-direction position — it read −$102.70 instead of 0 — so use the net pnl
      // converted at the figure's own date (TRY display keeps it raw).
      ? { pnl: isNonTryFrame ? (Number(convertAt(aggPnlTry, 'TRY', pnlFxDate)) || 0) : (Number(aggPnlTry) || 0),
          pnlPercent: aggPnlPercent, base: isNonTryFrame ? displayCurrency : 'TRY' }
      : frame(headCostTry, Number(aggMarketValueTry) || 0,
          entryDateIso ?? pnlFxDate, pnlFxDate, aggPnlTry, aggPnlPercent, headFrameSign);
  const framePnl = headFrame.base !== 'TRY' ? headFrame.pnl : null;
  const aggPnlPercentFramed = headFrame.pnlPercent ?? aggPnlPercent;
  const pnlSignVal = headFrame.pnl ?? aggPnlTry;

  // Market value must reconcile with the headline K/Z: value = entry cost + K/Z, both in the display frame.
  // Converting marketValueTry separately at a single (today) rate drifts from cost + pnl for a non-TRY frame
  // (a SHORT read ~4918 instead of the cost+K/Z 5020). Falls back to the single-date convert when no display
  // frame applies (TRY/native fallback), where it already equals the raw TRY value.
  const entryCostDisplay = (entryPriceConverted != null && aggQuantity != null)
    ? Number(entryPriceConverted) * Number(aggQuantity) : null;
  const marketValueDisplay = (framePnl != null && entryCostDisplay != null)
    ? entryCostDisplay + framePnl
    : marketValueConverted;

  const latestPoint = series.length > 0 ? series[series.length - 1] : null;
  // For a closed asset the series ends at its exit date; convert the daily card at that point's date
  // (not today) so the FX rate matches the day the figure was actually realized.
  const dailyPnlFxDate = latestPoint?.timestamp
    ? new Date(latestPoint.timestamp).toLocaleDateString('sv-SE')
    : todayIso;
  // A position CLOSED BEFORE today has no movement today — its value is realized/static — so the daily card
  // must read "—", not the stale close-day move from the series' last point (which read e.g. −$2.12 from the
  // exit day as if it were today). A position closed TODAY keeps its daily (today's move did happen).
  const isClosedBeforeToday = isFullyClosed && aggExitDateIso != null && aggExitDateIso < todayIso;
  const dailyPnlTry = isClosedBeforeToday ? null : (latestPoint?.dailyPnlTry ?? null);
  const dailyPnlPercent = isClosedBeforeToday ? null : (latestPoint?.dailyPnlPercent ?? null);
  // Daily K/Z in the display-currency frame: today's value vs yesterday's, each at its own date's FX, so a
  // EUR holding's daily reads ~0% in EUR instead of the raw EUR/TRY daily move. Prior date = the previous
  // series point; the universal frame() falls back to the TRY scalar when no USD/EUR frame applies.
  const prevSeriesPoint = series.length >= 2 ? series[series.length - 2] : null;
  const prevDailyDate = prevSeriesPoint?.timestamp
    ? new Date(prevSeriesPoint.timestamp).toLocaleDateString('sv-SE')
    : dailyPnlFxDate;
  const todayValueTry = Number(aggMarketValueTry) || 0;
  // VİOP foreign (USD/EUR) daily from the SHARED unit-price move, NOT the value−value frame: a hedge's LONG leg
  // framed its notional ($4373 base) and the SHORT its equity ($4750 base), so the same day-move read −$119.30
  // on the long and +$119.45 on the short (each base carries a different FX-drift). The contract's price move is
  // identical for both legs, so dir × (todayPrice − yesterdayPrice) × Σ(size·qty) — converted native→display —
  // gives one magnitude, opposite sign, and strips the FX drift. Spot / TRY display keep the frame() path.
  const viopForeignDaily = () => {
    if (!isViop || !isNonTryFrame || latestPoint == null || prevSeriesPoint == null) return null;
    // convertBetween (NOT convertAt) to force the contract's NATIVE currency: convertAt ignores the natural hint
    // when displayCurrency is USD/EUR and resolves to displayCurrency, which mis-converts a USD-quoted contract
    // shown in EUR. Prices in native → the move is FX-clean before the single native→display conversion below.
    const todayPrice = Number(convertBetween(latestPoint.unitPriceTry, 'TRY', nativeCurrency, dailyPnlFxDate));
    const yestPrice = Number(convertBetween(prevSeriesPoint.unitPriceTry, 'TRY', nativeCurrency, prevDailyDate));
    if (!Number.isFinite(todayPrice) || !Number.isFinite(yestPrice)) return null;
    const sizeQty = lots.reduce((sum, l) => l.exitDate != null ? sum
      : sum + (Number(l.quantity) || 0) * (Number(l.derivative?.contractSize) || 1), 0);
    if (!(sizeQty > 0)) return null;
    const nativeDaily = headDirectionSign * (todayPrice - yestPrice) * sizeQty;
    const disp = Number(convertBetween(nativeDaily, nativeCurrency, displayCurrency, dailyPnlFxDate));
    if (!Number.isFinite(disp)) return null;
    // Percent in the SAME (display) frame as the amount — the native price-move %, not the backend TRY % (which
    // would read a USD figure against a TRY base). LONG and SHORT of one symbol then read ∓ the same %.
    const pct = Math.abs(yestPrice) > 1e-9
      ? headDirectionSign * ((todayPrice - yestPrice) / Math.abs(yestPrice)) * 100
      : null;
    return { pnl: disp, pnlPercent: pct };
  };
  const viopDaily = dailyPnlTry == null ? null : viopForeignDaily();
  const dailyFrame = dailyPnlTry == null
    ? { pnl: null, pnlPercent: null, base: 'TRY' }
    : viopDaily != null
      ? { pnl: viopDaily.pnl, pnlPercent: viopDaily.pnlPercent, base: displayCurrency }
      : frame(todayValueTry - headDirectionSign * (Number(dailyPnlTry) || 0), todayValueTry,
          prevDailyDate, dailyPnlFxDate, dailyPnlTry, dailyPnlPercent, headDirectionSign);
  const dailyClass = getChangeClass(dailyFrame.pnl ?? dailyPnlTry);
  const pnlClass = getChangeClass(pnlSignVal);

  const rangeLayoutId = `asset-range-${direction || 'all'}`;

  return (
    <div className="space-y-5">
      <motion.div
        variants={cardVariants}
        initial="hidden"
        animate="show"
        className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3"
      >
        {STAT_CARD_DEFS.map(({ key, labelKey, Icon, format, money: isMoney }) => (
          <Card key={key} variant="elevated" radius="xl" padding="sm" interactive className="space-y-2">
            <div className="flex items-center gap-2">
              <div className="flex items-center justify-center w-6 h-6 rounded-md bg-accent/10">
                <Icon className="h-3 w-3 text-accent" />
              </div>
              <p className="text-[11px] text-fg-muted">{t(`assetDetail.stats.${labelKey}`)}</p>
            </div>
            {isMoney
              ? <FitMoney value={statRawFor(key)} base={targetCurrency} className="text-sm font-semibold font-mono text-fg" />
              : <p className="text-sm font-semibold font-mono text-fg">{format(aggAsset[key])}</p>}
          </Card>
        ))}
      </motion.div>

      <motion.div
        variants={cardVariants}
        initial="hidden"
        animate="show"
        className="grid grid-cols-1 sm:grid-cols-2 gap-3"
      >
        <Card variant="outline" tone={pnlSignVal >= 0 ? 'success' : 'danger'} radius="xl" padding="md" className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 shrink-0">
            {pnlSignVal >= 0
              ? <TrendingUp className="h-5 w-5 text-success" />
              : <TrendingDown className="h-5 w-5 text-danger" />}
            <span className="text-sm font-medium text-fg">{t('assetDetail.pnl')}</span>
          </div>
          <div className="text-right flex items-center gap-3 min-w-0">
            <span className={`shrink-0 inline-flex items-center rounded-md px-2 py-0.5 text-xs font-mono font-medium ${changeBg[pnlClass]} ${changeColors[pnlClass]}`}>
              {formatPercentSmart(aggPnlPercentFramed)}
            </span>
            {framePnl != null
              ? <FitMoney as="p" value={framePnl} base={displayCurrency} className={`flex-1 text-lg font-semibold font-mono ${changeColors[pnlClass]}`} />
              : <FitMoney as="p" value={aggPnlTry} base="TRY" natural={nativeCurrency} dateAt={pnlFxDate} className={`flex-1 text-lg font-semibold font-mono ${changeColors[pnlClass]}`} />}
          </div>
        </Card>
        <Card variant={dailyPnlTry == null ? 'elevated' : 'outline'} tone={dailyPnlTry == null ? 'default' : ((dailyFrame.pnl ?? 0) >= 0 ? 'success' : 'danger')} radius="xl" padding="md" className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 shrink-0">
            {dailyPnlTry == null
              ? <TrendingUp className="h-5 w-5 text-fg-muted" />
              : (dailyFrame.pnl ?? 0) >= 0
                ? <TrendingUp className="h-5 w-5 text-success" />
                : <TrendingDown className="h-5 w-5 text-danger" />}
            <span className="text-sm font-medium text-fg">{t('assetDetail.dailyPnl')}</span>
          </div>
          <div className="text-right flex items-center gap-3 min-w-0">
            {dailyPnlTry == null ? (
              <p className="text-sm text-fg-muted">—</p>
            ) : (
              <>
                <span className={`shrink-0 inline-flex items-center rounded-md px-2 py-0.5 text-xs font-mono font-medium ${changeBg[dailyClass]} ${changeColors[dailyClass]}`}>
                  {formatPercentSmart(dailyFrame.pnlPercent)}
                </span>
                {dailyFrame.base === 'TRY'
                  ? <FitMoney as="p" value={dailyPnlTry} base="TRY" natural={nativeCurrency} dateAt={dailyPnlFxDate} className={`flex-1 text-lg font-semibold font-mono ${changeColors[dailyClass]}`} />
                  : <FitMoney as="p" value={dailyFrame.pnl} base={dailyFrame.base} className={`flex-1 text-lg font-semibold font-mono ${changeColors[dailyClass]}`} />}
              </>
            )}
          </div>
        </Card>
      </motion.div>

      <Card
        as={motion.div}
        variants={cardVariants}
        initial="hidden"
        animate="show"
        variant="elevated"
        radius="xl"
        padding="md"
        backdropBlur
        className="space-y-3"
      >
        <div className="flex items-center justify-between flex-wrap gap-2">
          {lots.length > 0 ? (
            <div className="flex items-center gap-3">
              <div className="flex items-center gap-1.5">
                <span className="relative w-2 h-2">
                  <span className="absolute inset-0 rounded-full bg-success animate-ping opacity-30" />
                  <span className="relative block w-2 h-2 rounded-full bg-success" />
                </span>
                <span className="text-[10px] text-fg-muted font-medium">
                  {t(isViop
                    ? 'assetDetail.lots.lotMarkerOpen'
                    : 'portfolio.performance.lotAdded')}
                </span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="relative w-2 h-2">
                  <span className="absolute inset-0 rounded-full bg-danger animate-ping opacity-30" />
                  <span className="relative block w-2 h-2 rounded-full bg-danger" />
                </span>
                <span className="text-[10px] text-fg-muted font-medium">
                  {t(isViop
                    ? 'assetDetail.lots.lotMarkerClose'
                    : 'portfolio.performance.lotSoldOrClosed')}
                </span>
              </div>
            </div>
          ) : <span />}
          <RangeSelector value={range} onChange={setRange} layoutId={rangeLayoutId} size="sm" />
        </div>

        <div className="relative">
          {loading && (
            <div className="absolute inset-0 flex items-center justify-center z-10 bg-bg-elevated/60 rounded-lg">
              <Spinner size="md" tone="accent" />
            </div>
          )}
          {isUpdating && !loading && (
            <div className="absolute top-2 right-2 z-10 flex items-center gap-1.5 bg-accent/15 text-accent text-[11px] font-semibold px-2.5 py-1 rounded-full backdrop-blur-sm">
              <Spinner size="xs" tone="inherit" />
              {t('assetDetail.updating', { defaultValue: 'Güncelleniyor...' })}
            </div>
          )}
          {series.length === 0 && !loading ? (
            <div className="flex items-center justify-center h-[340px] text-sm text-fg-muted">
              {t('assetDetail.noDataInRange')}
            </div>
          ) : series.length > 0 ? (
            <AssetChart data={series} isDark={isDark} t={t} convertAt={convertAt} displayCurrency={displayCurrency} nativeCurrency={nativeCurrency} />
          ) : null}
        </div>
      </Card>

      {lots.length > 0 && (
        <Card
          as={motion.div}
          variants={cardVariants}
          initial="hidden"
          animate="show"
          variant="elevated"
          radius="xl"
          padding="md"
          backdropBlur
          className="space-y-3"
        >
          <LotsTable
            lots={lots}
            t={t}
            money={money}
            bigMoney={bigMoney}
            nativeCurrency={nativeCurrency}
            frame={frame}
            convertAt={convertAt}
            onEditLot={onEditLot}
            onSellLot={onSellLot}
            onReopenLot={onReopenLot}
            onDeleteLot={onDeleteLot}
          />
        </Card>
      )}
    </div>
  );
}
