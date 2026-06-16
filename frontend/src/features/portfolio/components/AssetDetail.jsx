import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import useChartRange from '../../../shared/hooks/useChartRange';
import { ArrowLeft, ExternalLink, Plus } from 'lucide-react';
import { TrendingUp, TrendingDown } from '../../../shared/components/feedback/AnimatedIcons';
import ReactECharts from 'echarts-for-react';
import { useTheme } from '../../../shared/context/useTheme';
import { useAssetSeries, useAssetAggregate, useBackfillStatus, isLotPending, useAssetLots } from '../hooks/usePortfolioData';
import { formatPercentSmart, changeColors, changeBg, getChangeClass } from '../../../shared/utils/formatters';
import { useMoney } from '../../../shared/hooks/useMoney';
import { useRateHistory } from '../../../shared/hooks/useRateHistory';
import { cardVariants } from '../../../shared/utils/animations';
import RangeSelector from '../../../shared/components/form/RangeSelector';
import PositionFormModal from './PositionFormModal';
import MarketOpenDerivativeModal from './MarketOpenDerivativeModal';
import Card from '../../../shared/components/card';
import Spinner from '../../../shared/components/feedback/Spinner';
import IconButton from '../../../shared/components/buttons/IconButton';
import { buildAssetChartOption } from '../lib/assetChartBuilder';
import FitMoney from '../../../shared/components/FitMoney';
import { resolveNativeCurrency } from '../lib/positionFormHelpers';
import { commodityLabel } from '../../../shared/utils/commodityName';
import { computeViopAggregate, computeClosedAggregate } from '../lib/assetAggregates';
import { processAssetSeries } from '../lib/assetSeriesProcessing';
import { LotsTable } from './LotsTable';
import { marketHref, STAT_CARD_DEFS, lotDirection } from '../lib/assetDetailHelpers';

function AssetChart({ data, isDark, t, convertAt, displayCurrency, nativeCurrency }) {
  const option = useMemo(
    () => buildAssetChartOption(data, isDark, t, convertAt, displayCurrency, nativeCurrency),
    [data, isDark, t, convertAt, displayCurrency, nativeCurrency]
  );
  if (!option) return null;
  return <ReactECharts option={option} notMerge lazyUpdate className="h-[260px] sm:h-[300px] lg:h-[340px]" opts={{ renderer: 'canvas' }} />;
}

/**
 * One position view — stat cards + K/Z + daily + chart + lots — for either the whole symbol ({@code direction}
 * null) or a single VİOP leg (LONG/SHORT). A same-symbol hedge renders two of these so each leg shows its own
 * direction-aware K/Z and chart instead of one netted spot-like blend; the per-direction backend aggregate +
 * series (fetched with {@code direction}) keep each leg's USD/EUR framing correct.
 */
function DirectionPanel({
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

export default function AssetDetail({ portfolioId, asset, onBack, onEditLot, onDeleteLot, onSellLot, onReopenLot, hasActiveDialog = false }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { isDark } = useTheme();
  const { format: money, formatCompact: moneyCompact } = useMoney();
  const nativeCurrency = resolveNativeCurrency({
    assetType: asset?.assetType,
    assetCode: asset?.assetCode,
  });
  const bigMoney = (v) => moneyCompact(v, 'TRY', 100_000, nativeCurrency);
  const { convertAt, convertBetween, currency: displayCurrency, frame } = useRateHistory();
  const [range, setRange] = useChartRange();
  const [addLotOpen, setAddLotOpen] = useState(false);

  const { data: lotData, isFetched: lotsFetched } = useAssetLots(portfolioId, asset.assetType, asset.assetCode);
  const lots = useMemo(() => lotData ?? [asset], [lotData, asset]);

  const backfill = useBackfillStatus(portfolioId);
  const isUpdating = isLotPending(backfill, asset.assetType, asset.assetCode);

  useEffect(() => {
    if (lotsFetched && lots.length === 0 && !isUpdating && !hasActiveDialog) {
      onBack?.();
    }
  }, [lotsFetched, lots.length, isUpdating, hasActiveDialog, onBack]);

  const isViop = asset.assetType === 'VIOP';
  const longLots = useMemo(() => lots.filter((l) => lotDirection(l) === 'LONG'), [lots]);
  const shortLots = useMemo(() => lots.filter((l) => lotDirection(l) === 'SHORT'), [lots]);
  // Split only a genuine same-symbol hedge: a VİOP symbol holding BOTH directions. A pure-direction or non-VİOP
  // asset keeps the single panel, so its behaviour is unchanged.
  const isMixed = isViop && longLots.length > 0 && shortLots.length > 0;

  const displayLabel = asset.assetCode;
  const displayBadge = asset.assetImage || null;
  const displayBadgeText = asset.assetCode.replace('.IS', '').slice(0, 3).toUpperCase();
  const anyLotOpen = lots.some((l) => l.exitDate == null);
  const rawAssetName = commodityLabel(t, asset.assetType, asset.assetCode,
    asset.assetName || t(`assets.labels.${asset.assetType}`, { defaultValue: asset.assetType }));
  const displaySub = anyLotOpen
    ? rawAssetName.replace(/\s·\sKAPALI$/, '')
    : rawAssetName;

  const panelProps = {
    portfolioId, asset, t, isDark, money, bigMoney, nativeCurrency, convertAt, convertBetween, displayCurrency, frame,
    range, setRange, isUpdating,
    onEditLot, onSellLot, onReopenLot, onDeleteLot,
  };

  const directionBadge = (dir, count) => (
    <div className={`flex items-center gap-2 ${dir === 'LONG' ? 'text-success' : 'text-danger'}`}>
      {dir === 'LONG' ? <TrendingUp className="h-4 w-4" /> : <TrendingDown className="h-4 w-4" />}
      <h2 className="text-sm font-bold tracking-wide">
        {t(`assetDetail.direction.${dir.toLowerCase()}`, { defaultValue: dir })}
      </h2>
      {count > 0 && (
        <span className="inline-flex items-center rounded-md bg-current/10 text-[10px] font-bold px-1.5 py-0.5">
          {t('assetDetail.lotCount', { count, defaultValue: '{{count}} lot' })}
        </span>
      )}
      <span className="h-px flex-1 bg-border-default/60" />
    </div>
  );

  return (
    <div className="space-y-5">
      <motion.div
        initial={{ opacity: 0, y: -16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
        className="flex items-center justify-between gap-3 flex-wrap"
      >
        <div className="flex items-center gap-3 min-w-0">
          <IconButton
            variant="secondary"
            size={9}
            shape="square"
            icon={<ArrowLeft className="h-4 w-4" />}
            aria-label={t('common.back')}
            onClick={onBack}
            className="hover:-translate-x-0.5 shrink-0"
          />
          <div className="flex items-center gap-3 min-w-0">
            {displayBadge ? (
              /^https?:\/\//i.test(displayBadge)
                ? <img src={displayBadge} alt={displayLabel} width={40} height={40} loading="lazy" className="w-10 h-10 rounded-xl object-cover" />
                : <span className="flex items-center justify-center w-10 h-10 rounded-xl text-2xl">{displayBadge}</span>
            ) : (
              <span className="flex items-center justify-center w-10 h-10 rounded-xl bg-accent/10 text-sm font-bold text-accent">
                {displayBadgeText}
              </span>
            )}
            <div className="min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <h1 className="text-lg sm:text-xl font-bold text-fg truncate max-w-[180px] sm:max-w-none">{displayLabel}</h1>
                {lots.length > 1 && !isMixed && (
                  <span className="inline-flex items-center rounded-md bg-accent/10 text-accent text-[10px] font-bold px-1.5 py-0.5 shrink-0">
                    {t('assetDetail.lotCount', { count: lots.length, defaultValue: '{{count}} lot' })}
                  </span>
                )}
              </div>
              <p className="text-xs text-fg-muted truncate">{displaySub}</p>
            </div>
          </div>
        </div>
        <div className="flex items-center gap-2 flex-wrap justify-end">
          <button
            type="button"
            onClick={() => navigate(marketHref(asset.assetType, asset.assetCode))}
            className="flex items-center gap-1.5 rounded-lg border border-border-default bg-bg-elevated px-2.5 sm:px-3 py-2 text-xs font-semibold text-fg-muted hover:text-accent hover:border-accent/40 hover:bg-accent/5 transition-all backdrop-blur-sm cursor-pointer"
            title={t('portfolio.positions.viewOnMarket', { defaultValue: 'Pazar detayı' })}
          >
            <ExternalLink className="h-3.5 w-3.5" />
            <span className="hidden sm:inline">{t('portfolio.positions.viewOnMarket', { defaultValue: 'Pazar detayı' })}</span>
          </button>
          <button
            onClick={() => setAddLotOpen(true)}
            className="flex items-center gap-2 rounded-lg bg-accent px-3 sm:px-4 py-2 text-sm font-semibold text-white hover:bg-accent-bright transition-all border-none cursor-pointer"
          >
            <Plus className="h-4 w-4" />
            {t('assetDetail.newLot')}
          </button>
        </div>
      </motion.div>

      {isMixed ? (
        <div className="space-y-8">
          <div className="space-y-3">
            {directionBadge('LONG', longLots.length)}
            <DirectionPanel {...panelProps} lots={longLots} direction="LONG" />
          </div>
          <div className="space-y-3">
            {directionBadge('SHORT', shortLots.length)}
            <DirectionPanel {...panelProps} lots={shortLots} direction="SHORT" />
          </div>
        </div>
      ) : (
        <DirectionPanel {...panelProps} lots={lots} direction={null} />
      )}

      {addLotOpen && asset.assetType === 'VIOP' && (
        <MarketOpenDerivativeModal
          assetCode={asset.assetCode}
          assetName={asset.assetName}
          currentPrice={asset.currentPriceTry}
          metadata={asset.metadata}
          onClose={() => setAddLotOpen(false)}
          onComplete={() => setAddLotOpen(false)}
        />
      )}
      {addLotOpen && asset.assetType !== 'VIOP' && (
        <PositionFormModal
          mode="add"
          portfolioId={portfolioId}
          asset={{
            type: asset.assetType,
            code: asset.assetCode,
            name: asset.assetName,
            image: asset.assetImage,
            currentPrice: asset.currentPriceTry,
          }}
          onClose={() => setAddLotOpen(false)}
          onComplete={() => setAddLotOpen(false)}
        />
      )}
    </div>
  );
}
