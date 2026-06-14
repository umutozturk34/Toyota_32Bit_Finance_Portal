import { useCallback, useMemo, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import useSessionState from '../../../shared/hooks/useSessionState';
import useChartRange from '../../../shared/hooks/useChartRange';
import ReactECharts from 'echarts-for-react';
import { TrendingUp } from '../../../shared/components/feedback/AnimatedIcons';
import { usePortfolioPerformance, useBackfillStatus } from '../hooks/usePortfolioData';
import { useRateHistory } from '../../../shared/hooks/useRateHistory';
import { formatPrice, fitMoney, formatPercentSmart } from '../../../shared/utils/formatters';
import { cardVariants } from '../../../shared/utils/animations';
import Card from '../../../shared/components/card';
import Spinner from '../../../shared/components/feedback/Spinner';
import { useTheme } from '../../../shared/context/useTheme';
import useElapsedSeconds from '../../../shared/hooks/useElapsedSeconds';
import RangeSelector from '../../../shared/components/form/RangeSelector';
import i18n from '../../../shared/i18n/config';
import ChartHoverReadout from '../../../shared/charts/ChartHoverReadout';
import {
  ASSET_TYPE_FILTERS as ASSET_TYPES,
  ASSET_TYPE_COLORS,
} from '../../../shared/constants/assetTypes';
import {
  POSITION_EVENT_META,
  themePalette,
  capPoints,
  nearestIndex,
  buildEChartsOption,
} from '../lib/performanceChartBuilder';

function PerformanceChart({ portfolioId, backfill: backfillProp, forPrint = false }) {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const { convertAt, currency } = useRateHistory();
  const chartRef = useRef(null);
  const [range, setRange] = useChartRange();
  const [activeType, setActiveType] = useSessionState('portfolio-perf-type', null);
  const [hoverIdx, setHoverIdx] = useState(null);

  const { data: perfData = [], isLoading } = usePortfolioPerformance(portfolioId, range, activeType);
  // Spinner only on the FIRST load (no cached data). A background refetch (range/type switch, tab re-entry)
  // keeps isFetching true while React Query still holds the previous data — blanking the chart to a spinner
  // then was the switch flicker; stale-while-revalidate keeps the existing chart visible during revalidation.
  const loading = isLoading;
  const ownBackfill = useBackfillStatus(backfillProp ? null : portfolioId);
  const backfill = backfillProp ?? ownBackfill;
  const backfillElapsed = useElapsedSeconds(backfill.since);

  const safeCurrency = currency === 'USD' || currency === 'EUR' ? currency : 'TRY';
  const money = useCallback((value) => {
    if (value == null) return '—';
    const abs = Math.abs(value);
    const maxDecimals = abs < 10 ? 4 : abs < 1000 ? 3 : 2;
    return formatPrice(value, { currency: safeCurrency, minDecimals: 2, maxDecimals });
  }, [safeCurrency]);

  // PnL in a non-TRY frame = value − entry-date-FX cost, supplied PER POINT by the backend. value/cost now
  // fold in closed lots locked at their exit-date FX, so value − cost is the TOTAL PnL directly — we must NOT
  // add cash on top (that double-counted the closed lots and re-drifted them at today's rate). realizedByCcy
  // is the closed slice. TRY frame uses the backend TRY scalars unchanged.
  // Cap to ~1500 points so the per-date FX conversion below and the hover scan stay cheap on a long history.
  const cappedPerf = useMemo(() => capPoints(perfData, 1500), [perfData]);
  const convertedPerfData = useMemo(() => {
    const ccy = safeCurrency;
    if (ccy === 'TRY') return cappedPerf;
    return cappedPerf.map((point) => {
      const dateStr = new Date(point.time).toLocaleDateString('sv-SE');
      const valueDisp = point.valueByCcy?.[ccy] ?? convertAt(point.value, 'TRY', dateStr);
      const costDisp = point.costBasisByCcy?.[ccy];
      const cashDisp = point.realizedByCcy?.[ccy] ?? convertAt(point.cash, 'TRY', dateStr) ?? 0;
      // Total PnL = pnlByCcy (open + realized, closed locked at exit FX). Falls back to value − cost, then to
      // a per-date TRY conversion. Never value − cost + cash (that double-counts realized).
      const pnlByCcy = point.pnlByCcy?.[ccy];
      const totalPnl = pnlByCcy ?? ((valueDisp != null && costDisp != null) ? valueDisp - costDisp : null);
      const pnlDisp = totalPnl != null ? totalPnl : convertAt(point.pnl, 'TRY', dateStr);
      const pnlPercent = costDisp ? (pnlDisp / Math.abs(costDisp)) * 100 : null;
      const details = (point.details || []).map((d) => {
        const dv = d.valueByCcy?.[ccy] ?? convertAt(d.valueTry, 'TRY', dateStr);
        const dc = d.costBasisByCcy?.[ccy];
        return { ...d, valueTry: dv, pnlTry: (dv != null && dc != null) ? dv - dc : convertAt(d.pnlTry, 'TRY', dateStr) };
      });
      const events = (point.events || []).map((e) => ({ ...e, valueTry: convertAt(e.valueTry, 'TRY', dateStr) }));
      return { ...point, value: valueDisp, pnl: pnlDisp, pnlPercent, cash: cashDisp, details, events };
    });
  }, [cappedPerf, convertAt, safeCurrency]);

  const currentValue = convertedPerfData.length > 0 ? convertedPerfData[convertedPerfData.length - 1] : null;
  const isRealized = activeType === 'CASH';
  const currentRealized = currentValue?.value ?? 0;
  const mainColor = isRealized
    ? (currentRealized < 0 ? '#ef4444' : '#10b981')
    : (activeType ? (ASSET_TYPE_COLORS[activeType] || '#6366f1') : '#6366f1');
  const palette = useMemo(() => themePalette(isDark), [isDark]);
  const option = useMemo(
    () => (convertedPerfData.length > 0 ? buildEChartsOption(convertedPerfData, mainColor, palette, money, forPrint) : null),
    [convertedPerfData, mainColor, palette, money, forPrint]
  );

  // Hover readout: the strip below the chart follows the cursor; with no hover it shows the latest
  // point, so it degrades gracefully even if the axisPointer event ever misfires.
  const activePoint = useMemo(() => {
    if (!convertedPerfData.length) return null;
    if (hoverIdx != null && convertedPerfData[hoverIdx]) return convertedPerfData[hoverIdx];
    return convertedPerfData[convertedPerfData.length - 1];
  }, [convertedPerfData, hoverIdx]);

  const onEvents = useMemo(() => ({
    updateAxisPointer: (e) => {
      const v = e?.axesInfo?.[0]?.value;
      if (v == null || !convertedPerfData.length) return;
      // O(log n) binary search instead of an O(n) scan on every mouse-move; setHoverIdx no-ops (React bails)
      // when the index is unchanged, so an unmoved hover does not re-render.
      setHoverIdx(nearestIndex(convertedPerfData, v));
    },
    globalout: () => setHoverIdx(null),
  }), [convertedPerfData]);

  const readout = useMemo(() => {
    if (!activePoint) return { date: '', fields: [] };
    const localeTag = i18n.t('common.localeTag');
    const date = new Date(activePoint.time).toLocaleDateString(localeTag, { day: '2-digit', month: 'short', year: 'numeric' });
    const fields = [];
    if (activePoint.value != null) fields.push({ key: 'val', value: money(activePoint.value), tone: 'muted' });
    if (activePoint.pnl != null) {
      const pct = activePoint.pnlPercent != null
        ? ` (${activePoint.pnl >= 0 ? '+' : ''}${Number(activePoint.pnlPercent).toFixed(2)}%)`
        : '';
      fields.push({ key: 'pnl', value: `${activePoint.pnl >= 0 ? '+' : ''}${money(activePoint.pnl)}${pct}`, tone: activePoint.pnl >= 0 ? 'pos' : 'neg' });
    }
    (activePoint.details || []).forEach((d, i) => {
      const isOther = d.label === 'OTHER';
      const color = isOther ? '#7d8590' : (ASSET_TYPE_COLORS[d.assetType] || '#6366f1');
      const label = isOther
        ? t('portfolio.allocation.otherLabel')
        : (d.label !== d.assetType ? d.label : t(`assets.labels.${d.assetType}`, { defaultValue: d.assetType }));
      fields.push({ key: `d${i}`, label, dot: color, value: `${d.pnlTry >= 0 ? '+' : ''}${money(d.pnlTry)}`, tone: d.pnlTry >= 0 ? 'pos' : 'neg' });
    });
    // Aggregate same type+asset events at this point so "2 lots of XAUTRYG added" reads as one
    // chip with the total quantity and count, instead of repeating "Lot Added XAUTRYG" twice.
    const evGroups = new Map();
    (activePoint.events || []).filter((e) => POSITION_EVENT_META[e.type]).forEach((e) => {
      const gk = `${e.type}|${e.assetCode || ''}`;
      const g = evGroups.get(gk) || { type: e.type, assetCode: e.assetCode, qty: 0, count: 0, value: 0 };
      g.qty += e.quantity != null ? Number(e.quantity) : 0;
      // valueTry is already FX-converted in convertedPerfData. For SOLD it's the proceeds (sold for),
      // for ADDED the entry value (bought for) — answers "ne kadar kapandı / açıldı".
      g.value += e.valueTry != null ? Number(e.valueTry) : 0;
      g.count += 1;
      evGroups.set(gk, g);
    });
    let ei = 0;
    evGroups.forEach((g) => {
      const meta = POSITION_EVENT_META[g.type];
      const qtyText = g.qty > 0 ? ` ×${g.qty.toLocaleString(localeTag, { maximumFractionDigits: 8 })}` : '';
      const countText = g.count > 1 ? ` (${g.count})` : '';
      const valText = g.value ? ` · ${money(g.value)}` : '';
      fields.push({ key: `e${ei}`, label: t(meta.labelKey), dot: meta.color, value: `${g.assetCode || ''}${qtyText}${countText}${valText}`, tone: 'muted' });
      ei += 1;
    });
    return { date, fields };
  }, [activePoint, money, t]);

  // Headline tracks the HOVERED point (activePoint falls back to the latest when not hovering), so the
  // top-left value / PnL / % always reflect exactly where the cursor sits — a static latest value while
  // hovering an earlier point is misleading.
  const headValue = activePoint?.value ?? null;
  const headPnl = activePoint?.pnl ?? null;
  const headPnlPercent = activePoint?.pnlPercent ?? null;
  const pnlPositive = isRealized
    ? (headValue ?? 0) >= 0
    : headPnl != null && headPnl >= 0;

  return (
    <motion.div variants={cardVariants} initial="hidden" animate="show">
      <Card variant="elevated" radius="2xl" padding="none" backdropBlur className="group">
        <div
          className="pointer-events-none absolute -top-20 -left-20 w-44 h-44 rounded-full blur-[80px] opacity-0 group-hover:opacity-60 transition-opacity duration-500"
          style={{ background: `radial-gradient(circle, ${mainColor}20 0%, transparent 70%)` }}
        />

        <div className="flex items-center justify-between p-4 sm:p-5 pb-0 gap-3 flex-wrap">
          <div className="flex items-center gap-3 min-w-0">
            <span className="flex items-center justify-center w-10 h-10 rounded-xl transition-transform duration-300 group-hover:scale-105" style={{ backgroundColor: mainColor + '15', boxShadow: `0 0 20px ${mainColor}10` }}>
              <TrendingUp className="h-4.5 w-4.5 text-accent" />
            </span>
            <div className="min-w-0">
              <p className="text-sm font-bold text-fg">
                {isRealized
                  ? t('portfolio.performance.realizedPnlTitle')
                  : activeType
                    ? t('portfolio.performance.titleByType', { type: t(`assets.labels.${activeType}`, { defaultValue: activeType }) })
                    : t('portfolio.performance.title')}
              </p>
              {activePoint && (
                <>
                  {readout.date && (
                    <p className="text-[11px] font-mono text-fg-muted mt-0.5 tabular-nums">{readout.date}</p>
                  )}
                  <div className="flex flex-wrap items-center gap-2.5 mt-0.5 min-w-0">
                    <span className="text-xl font-mono font-bold text-fg tracking-tight truncate max-w-full" title={money(headValue)}>{fitMoney(headValue, { currency: safeCurrency, maxChars: 14 })}</span>
                    {headPnl != null && (
                      <span className={`inline-flex items-center gap-1 max-w-full truncate text-xs font-mono font-semibold px-2 py-0.5 rounded-md ${pnlPositive ? 'text-success bg-success/10' : 'text-danger bg-danger/10'}`} title={`${pnlPositive ? '+' : ''}${money(headPnl)}`}>
                        {pnlPositive ? '+' : ''}{fitMoney(headPnl, { currency: safeCurrency, maxChars: 12 })} ({formatPercentSmart(headPnlPercent)})
                      </span>
                    )}
                  </div>
                </>
              )}
            </div>
          </div>

          <div className="flex items-center gap-3 flex-wrap">
            {backfill.running && (
              <div className="flex items-center gap-1.5 text-[10px] font-mono tracking-tight text-accent/90">
                <Spinner size="xs" tone="inherit" />
                <span>{t('portfolio.performance.calculating')} · {String(backfillElapsed).padStart(2, '0')}s</span>
              </div>
            )}
            <div className="flex items-center gap-3 flex-wrap">
              <div className="flex items-center gap-1.5">
                <span className="relative w-2 h-2">
                  <span className="absolute inset-0 rounded-full bg-success animate-ping opacity-30" />
                  <span className="relative block w-2 h-2 rounded-full bg-success" />
                </span>
                <span className="text-[10px] text-fg-muted font-medium">
                  {t('portfolio.performance.lotAdded')}
                </span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="relative w-2 h-2">
                  <span className="absolute inset-0 rounded-full bg-danger animate-ping opacity-30" />
                  <span className="relative block w-2 h-2 rounded-full bg-danger" />
                </span>
                <span className="text-[10px] text-fg-muted font-medium">
                  {t('portfolio.performance.lotSoldOrClosed', { defaultValue: 'Lot Sold/Closed' })}
                </span>
              </div>
            </div>
          </div>
        </div>

        <div className="flex items-center justify-between px-4 sm:px-5 pt-4 pb-2 gap-2 flex-wrap">
          <div className="flex max-w-full gap-0.5 overflow-x-auto rounded-xl border border-border-default bg-bg-base p-1 sm:inline-flex sm:max-w-none sm:flex-wrap">
            {[...ASSET_TYPES, { id: 'CASH' }].map(({ id }) => (
              <button
                key={id || 'all'}
                onClick={() => setActiveType(id)}
                className="relative shrink-0 rounded-lg px-2.5 sm:px-3 py-1.5 text-[11px] font-semibold transition-all border-none cursor-pointer bg-transparent"
              >
                {activeType === id && (
                  <motion.span
                    layoutId="perf-type"
                    className="absolute inset-0 rounded-lg bg-accent/15"
                    transition={{ type: 'spring', stiffness: 300, damping: 30 }}
                  />
                )}
                <span className={`relative z-10 ${activeType === id ? 'text-accent' : 'text-fg-muted hover:text-fg'}`}>
                  {id === 'CASH'
                    ? t('portfolio.performance.realizedPnlLabel')
                    : id
                      ? t(`assets.labels.${id}`, { defaultValue: id })
                      : t('assets.labels.ALL')}
                </span>
              </button>
            ))}
          </div>
          <div className="flex items-center gap-2 flex-wrap max-w-full">
            <div className="max-w-full overflow-x-auto">
              <RangeSelector value={range} onChange={setRange} layoutId="perf-range" size="md" />
            </div>
          </div>
        </div>

        <div className="relative min-h-[240px] sm:min-h-[360px] px-2">
          {loading ? (
            <div className="absolute inset-0 flex items-center justify-center">
              <Spinner size="md" tone="accent" />
            </div>
          ) : option ? (
            <ReactECharts
              ref={chartRef}
              key={`${isDark}-${currency}-${forPrint}`}
              option={option}
              notMerge
              lazyUpdate
              onEvents={onEvents}
              style={forPrint
                ? { height: 360, width: '100%', minHeight: 320, pointerEvents: 'none' }
                : { height: 'min(52vh, 360px)', minHeight: 240, width: '100%' }}
              opts={{ renderer: forPrint ? 'svg' : 'canvas' }}
            />
          ) : (
            <div className="flex flex-col items-center justify-center h-[240px] sm:h-[360px] gap-3">
              <TrendingUp className="h-8 w-8 text-fg-subtle" />
              <p className="text-sm text-fg-muted">{t('portfolio.performance.empty')}</p>
            </div>
          )}
        </div>
        {!forPrint && option && <ChartHoverReadout date={readout.date} fields={readout.fields} />}
      </Card>
    </motion.div>
  );
}

export default PerformanceChart;
