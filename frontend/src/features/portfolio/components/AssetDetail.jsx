import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import useChartRange from '../../../shared/hooks/useChartRange';
import { ArrowLeft, Hash, DollarSign, BarChart3, Wallet, Calendar, Plus, Pencil, Trash2, ShoppingBag, Layers, XCircle, RotateCcw } from 'lucide-react';
import { TrendingUp, TrendingDown } from '../../../shared/components/feedback/AnimatedIcons';
import ReactECharts from 'echarts-for-react';
import { useTheme } from '../../../shared/context/useTheme';
import { useAssetSeries, useAssetAggregate, useBackfillStatus, isLotPending } from '../hooks/usePortfolioData';
import { formatPercent, changeColors, changeBg, getChangeClass, currentLocaleTag } from '../../../shared/utils/formatters';
import { useMoney } from '../../../shared/hooks/useMoney';
import { useRateHistory } from '../../../shared/hooks/useRateHistory';
import { formatPrice } from '../../../shared/utils/formatters';
import { cardVariants } from '../../../shared/utils/animations';
import RangeSelector from '../../../shared/components/form/RangeSelector';
import PositionFormModal from './PositionFormModal';
import MarketOpenDerivativeModal from './MarketOpenDerivativeModal';
import PositionStatusBadge from './PositionStatusBadge';
import Card from '../../../shared/components/card';
import Spinner from '../../../shared/components/feedback/Spinner';
import IconButton from '../../../shared/components/buttons/IconButton';

const formatEntryDate = (v) => v ? new Date(v).toLocaleDateString(currentLocaleTag(), { day: '2-digit', month: 'short', year: 'numeric' }) : '—';

const STAT_CARD_DEFS = [
  { key: 'quantity', labelKey: 'quantity', Icon: Hash, format: (v) => Number(v).toLocaleString(currentLocaleTag(), { maximumFractionDigits: 6 }) },
  { key: 'entryDate', labelKey: 'entryDate', Icon: Calendar, format: formatEntryDate },
  { key: 'entryPrice', labelKey: 'entryPrice', Icon: DollarSign, money: true },
  { key: 'currentPriceTry', labelKey: 'currentPrice', Icon: BarChart3, money: true },
  { key: 'marketValueTry', labelKey: 'marketValue', Icon: Wallet, money: true },
];

const LINE_COLOR = '#6366f1';
const UNIT_COLOR = '#f59e0b';

function AssetChart({ data, isDark, t, convertAt, displayCurrency }) {
  const option = useMemo(
    () => buildAssetChartOption(data, isDark, t, convertAt, displayCurrency),
    [data, isDark, t, convertAt, displayCurrency]
  );
  if (!option) return null;
  return <ReactECharts option={option} notMerge lazyUpdate style={{ height: 340 }} opts={{ renderer: 'canvas' }} />;
}

function formatChartMoney(value, currency) {
  if (value == null || !Number.isFinite(value)) return 'N/A';
  const abs = Math.abs(value);
  if (abs >= 100_000) {
    return new Intl.NumberFormat(currentLocaleTag(), {
      notation: 'compact',
      style: 'currency',
      currency,
      maximumFractionDigits: 1,
    }).format(value);
  }
  const maxDecimals = abs < 10 ? 4 : abs < 1000 ? 3 : 2;
  return formatPrice(value, { currency, minDecimals: 2, maxDecimals });
}

function buildAssetChartOption(data, isDark, t, convertAt, displayCurrency) {
  if (!data || data.length === 0) return null;

  const muted = isDark ? '#6b6b7a' : '#94a3b8';
  const grid = isDark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.05)';
  const tooltipBg = isDark ? 'rgba(12,12,20,0.95)' : 'rgba(255,255,255,0.97)';
  const tooltipFg = isDark ? '#e2e2ea' : '#1a1a2e';
  const tooltipBorder = isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.08)';
  const targetCurrency = displayCurrency === 'ORIGINAL' || !displayCurrency ? 'TRY' : displayCurrency;

  const seriesData = data.map((d) => {
    const dateStr = new Date(d.timestamp).toISOString().slice(0, 10);
    return {
      value: [new Date(d.timestamp).getTime(), Number(convertAt(d.marketValueTry, 'TRY', dateStr) ?? 0)],
      unitPrice: Number(convertAt(d.unitPriceTry, 'TRY', dateStr) ?? 0),
      quantity: Number(d.quantity ?? 0),
      events: d.events || [],
    };
  });

  const values = seriesData.map((d) => d.value[1]);
  const dataMin = Math.min(...values);
  const dataMax = Math.max(...values);
  const span = dataMax - dataMin;
  const padding = span > 0 ? span * 0.08 : dataMax * 0.05;

  const seriesTimes = seriesData.map((d) => d.value[0]);
  let xMin = Math.min(...seriesTimes);
  let xMax = Math.max(...seriesTimes);
  if (xMin === xMax) {
    const halfDay = 12 * 3600 * 1000;
    xMin -= halfDay;
    xMax += halfDay;
  }

  const uniqueXCount = new Set(seriesData.map((d) => d.value[0])).size;
  const showZoom = data.length >= 2 && uniqueXCount >= 2;
  return {
    backgroundColor: 'transparent',
    animation: data.length < 200,
    grid: { left: 65, right: 24, top: 34, bottom: showZoom ? 92 : 40 },
    dataZoom: showZoom ? [
      {
        type: 'inside',
        xAxisIndex: 0,
        filterMode: 'none',
        zoomOnMouseWheel: true,
        moveOnMouseMove: true,
        moveOnMouseWheel: false,
        preventDefaultMouseMove: true,
      },
      {
        type: 'slider',
        xAxisIndex: 0,
        height: 26,
        bottom: 8,
        filterMode: 'none',
        borderColor: 'transparent',
        backgroundColor: 'transparent',
        dataBackground: {
          lineStyle: { color: '#6366f160', width: 1 },
          areaStyle: { color: '#6366f120' },
        },
        selectedDataBackground: {
          lineStyle: { color: '#6366f1', width: 1 },
          areaStyle: { color: '#6366f140' },
        },
        fillerColor: 'rgba(99,102,241,0.12)',
        handleStyle: { color: '#6366f1', borderColor: '#6366f1' },
        moveHandleStyle: { color: '#6366f1', opacity: 0.4 },
        showDetail: false,
        brushSelect: false,
      },
    ] : [],
    tooltip: {
      trigger: 'axis',
      backgroundColor: tooltipBg,
      borderColor: tooltipBorder,
      textStyle: { color: tooltipFg, fontSize: 11 },
      formatter: (params) => {
        const point = params?.[0]?.data;
        if (!point) return '';
        const date = new Date(point.value[0]).toLocaleDateString(currentLocaleTag(), { day: '2-digit', month: 'short', year: 'numeric' });
        const market = formatChartMoney(point.value[1], targetCurrency);
        const unit = formatChartMoney(point.unitPrice, targetCurrency);
        const marketLabel = t('assetDetail.marketValue');
        const unitLabel = t('assetDetail.unitPrice');
        return `
          <div style="padding:6px 2px;min-width:180px">
            <div style="font-size:10px;color:${tooltipFg};opacity:0.65;margin-bottom:6px">${date}</div>
            <div style="display:flex;justify-content:space-between;gap:14px;font-size:11px;margin-bottom:3px">
              <span style="display:flex;align-items:center;gap:5px;color:${tooltipFg};opacity:0.85"><span style="display:inline-block;width:6px;height:6px;border-radius:999px;background:${LINE_COLOR}"></span>${marketLabel}</span>
              <span style="font-family:ui-monospace,monospace;font-weight:600;color:${LINE_COLOR}">${market}</span>
            </div>
            <div style="display:flex;justify-content:space-between;gap:14px;font-size:11px">
              <span style="display:flex;align-items:center;gap:5px;color:${tooltipFg};opacity:0.85"><span style="display:inline-block;width:6px;height:6px;border-radius:999px;background:${UNIT_COLOR}"></span>${unitLabel}</span>
              <span style="font-family:ui-monospace,monospace;font-weight:600;color:${UNIT_COLOR}">${unit}</span>
            </div>
          </div>`;
      },
    },
    xAxis: {
      type: 'time',
      min: xMin,
      max: xMax,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: muted, fontSize: 10,
        hideOverlap: true,
        formatter: (val) => {
          const d = new Date(val);
          return `${d.getDate()} ${d.toLocaleString(currentLocaleTag(), { month: 'short' })}`;
        },
      },
      minInterval: 24 * 3600 * 1000,
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      min: Math.max(0, dataMin - padding),
      max: dataMax + padding,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: muted, fontSize: 10, formatter: (val) => formatChartMoney(val, targetCurrency) },
      splitLine: { lineStyle: { color: grid, type: 'dashed' } },
    },
    series: [{
      name: t('assetDetail.marketValue'),
      type: 'line',
      smooth: data.length < 200,
      showSymbol: false,
      sampling: 'lttb',
      data: seriesData,
      itemStyle: { color: LINE_COLOR },
      lineStyle: { width: 2.2, color: LINE_COLOR },
      areaStyle: seriesData.length < 2 ? undefined : {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: `${LINE_COLOR}55` },
            { offset: 1, color: `${LINE_COLOR}00` },
          ],
        },
      },
      markPoint: buildEventMarkPoints(seriesData),
    }],
  };
}

const POSITION_EVENT_TYPES = new Set(['POSITION_ADDED', 'POSITION_SOLD']);

function buildEventMarkPoints(seriesData) {
  const data = seriesData
    .filter((d) => (d.events || []).some((e) => POSITION_EVENT_TYPES.has(e.type)))
    .map((d) => {
      const evs = d.events.filter((e) => POSITION_EVENT_TYPES.has(e.type));
      const hasSold = evs.some((e) => e.type === 'POSITION_SOLD');
      const hasAdded = evs.some((e) => e.type === 'POSITION_ADDED');
      const color = hasSold && hasAdded ? '#f59e0b' : hasSold ? '#ef4444' : '#10b981';
      const borderColor = color === '#ef4444' ? '#1f0a0a' : color === '#f59e0b' ? '#2a1a05' : '#0a1f17';
      return {
        coord: [d.value[0], d.value[1]],
        itemStyle: { color, borderColor, borderWidth: 2, shadowColor: color + '99', shadowBlur: 8 },
      };
    });
  if (data.length === 0) return undefined;
  return {
    symbol: 'circle',
    symbolSize: 12,
    label: { show: false },
    emphasis: { scale: 1.3, label: { show: false } },
    animation: false,
    data,
  };
}

export default function AssetDetail({ portfolioId, asset, lots = [], onBack, onEditLot, onDeleteLot, onSellLot, onReopenLot, hasActiveDialog = false }) {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const { format: money, formatCompact: moneyCompact } = useMoney();
  const bigMoney = (v) => moneyCompact(v, 'TRY', 100_000);
  const { convertAt, currency: displayCurrency } = useRateHistory();
  const [range, setRange] = useChartRange();
  const [addLotOpen, setAddLotOpen] = useState(false);

  const { data: rawSeries = [], isLoading: loading } = useAssetSeries(
    portfolioId, asset.assetType, asset.assetCode, range
  );
  const seriesEndTs = useMemo(() => {
    if (lots.length === 0) return null;
    const anyOpen = lots.some((l) => l.exitDate == null);
    if (anyOpen) return null;
    const exits = lots
      .map((l) => {
        if (!l.exitDate) return null;
        const d = new Date(l.exitDate);
        d.setHours(23, 59, 59, 999);
        return d.getTime();
      })
      .filter((n) => n != null);
    return exits.length > 0 ? Math.max(...exits) : null;
  }, [lots]);
  const series = useMemo(() => {
    if (seriesEndTs == null) return rawSeries;
    return rawSeries.filter((p) => new Date(p.timestamp).getTime() <= seriesEndTs);
  }, [rawSeries, seriesEndTs]);
  const backfill = useBackfillStatus(portfolioId);
  const isUpdating = isLotPending(backfill, asset.assetType, asset.assetCode);

  useEffect(() => {
    if (lots.length === 0 && !isUpdating && !hasActiveDialog) {
      onBack?.();
    }
  }, [lots.length, isUpdating, hasActiveDialog, onBack]);

  const { data: aggregate } = useAssetAggregate(
    portfolioId,
    asset.assetType === 'VIOP' ? null : asset.assetType,
    asset.assetType === 'VIOP' ? null : asset.assetCode,
  );

  const viopAggregate = useMemo(() => {
    if (asset.assetType !== 'VIOP') return null;
    const openLots = lots.filter((l) => l.exitDate == null);
    if (openLots.length === 0) return null;
    let totalQty = 0;
    let weightedNum = 0;
    let totalMarket = 0;
    let totalPnl = 0;
    let earliest = null;
    let currentPrice = null;
    for (const l of openLots) {
      const q = Number(l.quantity) || 0;
      const ep = Number(l.entryPrice) || 0;
      totalQty += q;
      weightedNum += q * ep;
      totalMarket += Number(l.marketValueTry) || 0;
      totalPnl += Number(l.pnlTry) || 0;
      if (l.entryDate && (!earliest || new Date(l.entryDate) < new Date(earliest))) {
        earliest = l.entryDate;
      }
      if (currentPrice == null && l.currentPriceTry != null) currentPrice = Number(l.currentPriceTry);
    }
    const weightedAvg = totalQty > 0 ? weightedNum / totalQty : 0;
    const pnlPercent = weightedAvg > 0 && totalQty > 0
      ? (totalPnl / (weightedAvg * totalQty)) * 100
      : 0;
    return {
      lotCount: openLots.length,
      totalQuantity: totalQty,
      weightedAvgEntryPrice: weightedAvg,
      earliestEntryDate: earliest,
      currentPriceTry: currentPrice,
      totalMarketValueTry: totalMarket,
      totalPnlTry: totalPnl,
      pnlPercent,
    };
  }, [asset.assetType, lots]);

  const closedAggregate = useMemo(() => {
    if (lots.length === 0) return null;
    const allClosed = lots.every((l) => l.exitDate != null);
    if (!allClosed) return null;
    let totalEntryQty = 0;
    let weightedNum = 0;
    let totalRealizedPnl = 0;
    let earliest = null;
    for (const l of lots) {
      const q = Number(l.quantity) || 0;
      const ep = Number(l.entryPrice) || 0;
      totalEntryQty += q;
      weightedNum += q * ep;
      totalRealizedPnl += Number(l.realizedPnlTry ?? l.pnlTry ?? 0);
      if (l.entryDate && (!earliest || new Date(l.entryDate) < new Date(earliest))) {
        earliest = l.entryDate;
      }
    }
    const weightedAvg = totalEntryQty > 0 ? weightedNum / totalEntryQty : 0;
    const pnlPercent = weightedAvg > 0 && totalEntryQty > 0
      ? (totalRealizedPnl / (weightedAvg * totalEntryQty)) * 100
      : 0;
    return {
      lotCount: lots.length,
      totalQuantity: 0,
      weightedAvgEntryPrice: weightedAvg,
      earliestEntryDate: earliest,
      currentPriceTry: 0,
      totalMarketValueTry: 0,
      totalPnlTry: totalRealizedPnl,
      pnlPercent,
    };
  }, [lots]);

  const effectiveAggregate = (aggregate && aggregate.lotCount > 0 ? aggregate : viopAggregate) || closedAggregate;
  const hasAggregate = effectiveAggregate && effectiveAggregate.lotCount > 0;
  const aggQuantity = hasAggregate ? effectiveAggregate.totalQuantity : asset.quantity;
  const aggEntryDate = hasAggregate ? effectiveAggregate.earliestEntryDate : asset.entryDate;
  const aggEntryPriceTry = hasAggregate ? effectiveAggregate.weightedAvgEntryPrice : asset.entryPrice;
  const aggCurrentPriceTry = hasAggregate ? effectiveAggregate.currentPriceTry : asset.currentPriceTry;
  const aggMarketValueTry = hasAggregate ? effectiveAggregate.totalMarketValueTry : asset.marketValueTry;
  const aggPnlTry = hasAggregate ? effectiveAggregate.totalPnlTry : asset.pnlTry;
  const aggPnlPercent = hasAggregate ? effectiveAggregate.pnlPercent : asset.pnlPercent;
  const lotCount = hasAggregate ? effectiveAggregate.lotCount : (lots.length || 1);

  const entryDateIso = aggEntryDate ? new Date(aggEntryDate).toISOString().slice(0, 10) : null;
  const todayIso = new Date().toISOString().slice(0, 10);
  const targetCurrency = displayCurrency === 'ORIGINAL' || !displayCurrency ? 'TRY' : displayCurrency;
  const entryPriceConverted = entryDateIso ? convertAt(aggEntryPriceTry, 'TRY', entryDateIso) : null;
  const currentPriceConverted = convertAt(aggCurrentPriceTry, 'TRY', todayIso);
  const marketValueConverted = convertAt(aggMarketValueTry, 'TRY', todayIso);
  const aggAsset = { ...asset, quantity: aggQuantity, entryDate: aggEntryDate };
  const statValueFor = (key) => {
    if (key === 'entryPrice') return formatChartMoney(entryPriceConverted, targetCurrency);
    if (key === 'currentPriceTry') return formatChartMoney(currentPriceConverted, targetCurrency);
    if (key === 'marketValueTry') return formatChartMoney(marketValueConverted, targetCurrency);
    return null;
  };

  const latestPoint = series.length > 0 ? series[series.length - 1] : null;
  const dailyPnlTry = latestPoint?.dailyPnlTry ?? null;
  const dailyPnlPercent = latestPoint?.dailyPnlPercent ?? null;
  const dailyClass = getChangeClass(dailyPnlTry);
  const pnlClass = getChangeClass(aggPnlTry);
  const displayLabel = asset.assetCode;
  const displayBadge = asset.assetImage || null;
  const displayBadgeText = asset.assetCode.replace('.IS', '').slice(0, 3).toUpperCase();
  const anyLotOpen = lots.some((l) => l.exitDate == null);
  const rawAssetName = asset.assetName || t(`assets.labels.${asset.assetType}`, { defaultValue: asset.assetType });
  const displaySub = anyLotOpen
    ? rawAssetName.replace(/\s*·\s*KAPALI\s*$/i, '')
    : rawAssetName;

  return (
    <div className="space-y-5">
      <motion.div
        initial={{ opacity: 0, y: -16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
        className="flex items-center justify-between gap-3"
      >
        <div className="flex items-center gap-3">
          <IconButton
            variant="secondary"
            size={9}
            shape="square"
            icon={<ArrowLeft className="h-4 w-4" />}
            aria-label="back"
            onClick={onBack}
            className="hover:-translate-x-0.5"
          />
          <div className="flex items-center gap-3">
            {displayBadge ? (
              /^https?:\/\//i.test(displayBadge)
                ? <img src={displayBadge} alt={displayLabel} className="w-10 h-10 rounded-xl" />
                : <span className="flex items-center justify-center w-10 h-10 rounded-xl text-2xl">{displayBadge}</span>
            ) : (
              <span className="flex items-center justify-center w-10 h-10 rounded-xl bg-accent/10 text-sm font-bold text-accent">
                {displayBadgeText}
              </span>
            )}
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-xl font-bold text-fg">{displayLabel}</h1>
                {lotCount > 1 && (
                  <span className="inline-flex items-center rounded-md bg-accent/10 text-accent text-[10px] font-bold px-1.5 py-0.5">
                    {t('assetDetail.lotCount', { count: lotCount, defaultValue: '{{count}} lot' })}
                  </span>
                )}
              </div>
              <p className="text-xs text-fg-muted">{displaySub}</p>
            </div>
          </div>
        </div>
        <button
          onClick={() => setAddLotOpen(true)}
          className="flex items-center gap-2 rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-white hover:bg-accent-bright transition-all border-none cursor-pointer"
        >
          <Plus className="h-4 w-4" />
          {t('assetDetail.newLot')}
        </button>
      </motion.div>

      <motion.div
        variants={cardVariants}
        initial="hidden"
        animate="show"
        className="grid grid-cols-2 sm:grid-cols-5 gap-3"
      >
        {STAT_CARD_DEFS.map(({ key, labelKey, Icon, format, money: isMoney }) => (
          <Card key={key} variant="elevated" radius="xl" padding="sm" interactive className="space-y-2">
            <div className="flex items-center gap-2">
              <div className="flex items-center justify-center w-6 h-6 rounded-md bg-accent/10">
                <Icon className="h-3 w-3 text-accent" />
              </div>
              <p className="text-[11px] text-fg-muted">{t(`assetDetail.stats.${labelKey}`)}</p>
            </div>
            <p className="text-sm font-semibold font-mono text-fg">{isMoney ? statValueFor(key) : format(aggAsset[key])}</p>
          </Card>
        ))}
      </motion.div>

      <motion.div
        variants={cardVariants}
        initial="hidden"
        animate="show"
        className="grid grid-cols-1 sm:grid-cols-2 gap-3"
      >
        <Card variant="outline" tone={aggPnlTry >= 0 ? 'success' : 'danger'} radius="xl" padding="md" className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            {aggPnlTry >= 0
              ? <TrendingUp className="h-5 w-5 text-success" />
              : <TrendingDown className="h-5 w-5 text-danger" />}
            <span className="text-sm font-medium text-fg">{t('assetDetail.pnl')}</span>
          </div>
          <div className="text-right flex items-center gap-3">
            <span className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-mono font-medium ${changeBg[pnlClass]} ${changeColors[pnlClass]}`}>
              {formatPercent(aggPnlPercent)}
            </span>
            <p className={`text-lg font-semibold font-mono ${changeColors[pnlClass]}`}>
              {money(aggPnlTry)}
            </p>
          </div>
        </Card>
        <Card variant={dailyPnlTry == null ? 'elevated' : 'outline'} tone={dailyPnlTry == null ? 'default' : (dailyPnlTry >= 0 ? 'success' : 'danger')} radius="xl" padding="md" className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            {dailyPnlTry == null
              ? <TrendingUp className="h-5 w-5 text-fg-muted" />
              : dailyPnlTry >= 0
                ? <TrendingUp className="h-5 w-5 text-success" />
                : <TrendingDown className="h-5 w-5 text-danger" />}
            <span className="text-sm font-medium text-fg">{t('assetDetail.dailyPnl')}</span>
          </div>
          <div className="text-right flex items-center gap-3">
            {dailyPnlTry == null ? (
              <p className="text-sm text-fg-muted">—</p>
            ) : (
              <>
                <span className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-mono font-medium ${changeBg[dailyClass]} ${changeColors[dailyClass]}`}>
                  {formatPercent(dailyPnlPercent)}
                </span>
                <p className={`text-lg font-semibold font-mono ${changeColors[dailyClass]}`}>
                  {money(dailyPnlTry)}
                </p>
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
                  {t(asset.assetType === 'VIOP'
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
                  {t(asset.assetType === 'VIOP'
                    ? 'assetDetail.lots.lotMarkerClose'
                    : 'portfolio.performance.lotSoldOrClosed')}
                </span>
              </div>
            </div>
          ) : <span />}
          <RangeSelector value={range} onChange={setRange} layoutId="asset-range" size="sm" />
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
            <AssetChart data={series} isDark={isDark} t={t} convertAt={convertAt} displayCurrency={displayCurrency} />
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
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Layers className="h-4 w-4 text-accent" />
              <span className="text-sm font-semibold text-fg">
                {t('assetDetail.lots.title', { defaultValue: 'Lotlar' })}
              </span>
              <span className="text-[10px] font-mono text-fg-muted bg-bg-elevated px-1.5 py-0.5 rounded">
                {lots.length}
              </span>
            </div>
          </div>

          <div className="hidden md:grid md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_72px_minmax(0,0.7fr)_minmax(0,1fr)_minmax(0,1.2fr)_minmax(0,1.2fr)_minmax(0,1fr)] gap-3 px-3 py-1 text-[10px] uppercase tracking-wider text-fg-muted font-medium whitespace-nowrap">
            <span>{t('portfolio.positions.entryDateCol')}</span>
            <span>{t('portfolio.positions.exitDateLabel')}</span>
            <span>{t('portfolio.positions.statusCol')}</span>
            <span>{t('portfolio.positions.quantityCol')}</span>
            <span>{t('portfolio.positions.entryPriceCol')}</span>
            <span>{t('portfolio.positions.marketValueCol')}</span>
            <span>{t('portfolio.positions.pnlCol')}</span>
            <span>{t('portfolio.positions.actionsCol')}</span>
          </div>

          <div className="space-y-1.5">
            {lots.map((lot) => {
              const lotPnlClass = getChangeClass(lot.pnlTry);
              const isLotClosed = !!lot.exitDate;
              return (
                <motion.div
                  key={lot.id}
                  layout
                  initial={{ opacity: 0, y: 4 }}
                  animate={{ opacity: 1, y: 0 }}
                  className={`grid grid-cols-2 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_72px_minmax(0,0.7fr)_minmax(0,1fr)_minmax(0,1.2fr)_minmax(0,1.2fr)_minmax(0,1fr)] gap-3 items-center px-3 py-2 rounded-lg border border-border-default min-w-0 ${
                    isLotClosed ? 'bg-bg-elevated/50 opacity-70' : 'bg-bg-elevated hover:border-accent/40'
                  } transition-colors`}
                >
                  <span className="text-[11px] font-mono text-fg-muted text-left truncate">
                    {formatEntryDate(lot.entryDate)}
                  </span>
                  <span className="text-[11px] font-mono text-fg-muted text-left truncate">
                    {lot.exitDate ? formatEntryDate(lot.exitDate) : '—'}
                  </span>
                  <div className="flex justify-start">
                    <PositionStatusBadge closed={isLotClosed} isDerivative={lot.assetType === 'VIOP'} />
                  </div>
                  <span className="text-[11px] font-mono text-fg text-left truncate">
                    {Number(lot.quantity).toLocaleString(currentLocaleTag(), { maximumFractionDigits: 6 })}
                  </span>
                  <span className="text-[11px] font-mono text-fg text-left truncate">
                    {money(lot.entryPrice)}
                  </span>
                  <span className="text-[11px] font-mono text-fg text-left truncate" title={money(lot.marketValueTry)}>
                    {bigMoney(lot.marketValueTry)}
                  </span>
                  <div className="text-left min-w-0">
                    <p className={`text-[11px] font-mono font-semibold ${changeColors[lotPnlClass]} truncate`} title={money(lot.pnlTry)}>
                      {bigMoney(lot.pnlTry)}
                    </p>
                    <span className={`inline-flex items-center rounded px-1 py-0.5 text-[10px] font-mono ${changeColors[lotPnlClass]}`}>
                      {formatPercent(lot.pnlPercent)}
                    </span>
                  </div>
                  <div className="flex justify-start gap-1">
                    {!isLotClosed && onEditLot && (
                      <button
                        onClick={() => onEditLot(lot)}
                        className="flex items-center justify-center w-6 h-6 rounded-md text-accent bg-accent/10 hover:bg-accent/20 transition-colors border-none cursor-pointer"
                        aria-label={t('common.edit')}
                      >
                        <Pencil className="h-3 w-3" />
                      </button>
                    )}
                    {!isLotClosed && onSellLot && (
                      <button
                        onClick={() => onSellLot(lot)}
                        className="flex items-center justify-center w-6 h-6 rounded-md text-warning bg-warning/10 hover:bg-warning/20 transition-colors border-none cursor-pointer"
                        aria-label={lot.assetType === 'VIOP'
                          ? t('portfolio.derivatives.closeTitle', 'Pozisyon Kapat')
                          : t('portfolio.sell.title', { code: lot.assetCode })}
                      >
                        {lot.assetType === 'VIOP'
                          ? <XCircle className="h-3 w-3" />
                          : <ShoppingBag className="h-3 w-3" />}
                      </button>
                    )}
                    {isLotClosed && onReopenLot && (
                      <button
                        onClick={() => onReopenLot(lot)}
                        className="flex items-center justify-center w-6 h-6 rounded-md text-success bg-success/10 hover:bg-success/20 transition-colors border-none cursor-pointer"
                        aria-label={t('portfolio.reopen.title', 'Tekrar aç')}
                      >
                        <RotateCcw className="h-3 w-3" />
                      </button>
                    )}
                    {onDeleteLot && (
                      <button
                        onClick={() => onDeleteLot(lot)}
                        className="flex items-center justify-center w-6 h-6 rounded-md text-danger bg-danger/10 hover:bg-danger/20 transition-colors border-none cursor-pointer"
                        aria-label={t('common.delete')}
                      >
                        <Trash2 className="h-3 w-3" />
                      </button>
                    )}
                  </div>
                </motion.div>
              );
            })}
          </div>
        </Card>
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
