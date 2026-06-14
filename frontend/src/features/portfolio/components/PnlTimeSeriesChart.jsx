import { useCallback, useMemo, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import ReactECharts from 'echarts-for-react';
import { useRateHistory } from '../../../shared/hooks/useRateHistory';
import { useTheme } from '../../../shared/context/useTheme';
import { formatPrice } from '../../../shared/utils/formatters';
import { cardVariants } from '../../../shared/utils/animations';
import Card from '../../../shared/components/card';
import Spinner from '../../../shared/components/feedback/Spinner';
import RangeSelector from '../../../shared/components/form/RangeSelector';
import ChartHoverReadout from '../../../shared/charts/ChartHoverReadout';
import i18n from '../../../shared/i18n/config';
import {
  timeAxis,
  valueAxis,
  dataZoomBlock,
  lineSeriesDefaults,
  legendBase,
} from '../../../shared/charts/echartsTheme';

function themePalette(isDark) {
  return isDark
    ? { bg: 'rgba(12,12,20,0.96)', fg: '#e2e2ea', muted: '#6b6b7a', border: 'rgba(255,255,255,0.08)', grid: 'rgba(255,255,255,0.05)' }
    : { bg: 'rgba(255,255,255,0.98)', fg: '#1a1a2e', muted: '#94a3b8', border: 'rgba(0,0,0,0.08)', grid: 'rgba(0,0,0,0.04)' };
}

// Binary search for the point nearest `t` (points are time-ascending) — O(log n) instead of an O(n) scan
// on every mouse-move, which janked/froze the chart for a long history (e.g. a 31-year-old lot → ~11k pts).
function nearestPoint(points, t) {
  if (!points || points.length === 0) return null;
  if (t == null) return points[points.length - 1];
  let lo = 0;
  let hi = points.length - 1;
  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (points[mid].time < t) lo = mid + 1; else hi = mid;
  }
  if (lo > 0 && Math.abs(points[lo - 1].time - t) <= Math.abs(points[lo].time - t)) return points[lo - 1];
  return points[lo];
}

// Cap a long series to ~MAX points (uniform stride, endpoints kept) so the per-point FX conversion and
// re-render stay cheap. ECharts' own lttb sampling then fits it to the pixel width. A 31-year daily history
// (~11k points) × USD per-date conversion froze the browser; capping removes the freeze with no visible
// change to the line's shape.
function capPoints(points, max = 1500) {
  if (!points || points.length <= max) return points || [];
  const out = [];
  const step = (points.length - 1) / (max - 1);
  for (let i = 0; i < max; i += 1) out.push(points[Math.round(i * step)]);
  return out;
}

function buildOption(convertedSeries, palette, money) {
  const showZoom = convertedSeries.some((s) => s.data.length >= 2);
  const zoomBlock = dataZoomBlock(palette, { filterMode: 'none', height: 26 });
  if (zoomBlock[0]) zoomBlock[0].filterMode = 'none';
  // Floor the y-range for a near-constant series so sub-cent FX-rounding jitter on a non-moving (closed/hedged)
  // position isn't magnified by auto-zoom into "dramatic" ±0.0001 waves (mirrors PerformanceChart).
  const yVals = convertedSeries
    .flatMap((s) => (s.data || []).map((d) => Number(d.value?.[1])))
    .filter((n) => Number.isFinite(n));
  const dataMin = yVals.length ? Math.min(...yVals) : 0;
  const dataMax = yVals.length ? Math.max(...yVals) : 0;
  const span = dataMax - dataMin;
  const scale = Math.max(Math.abs(dataMax), Math.abs(dataMin));
  const flat = span <= Math.max(scale * 1e-4, 1e-6);
  const pad = flat ? Math.max(scale * 0.05, 1) : 0;
  return {
    backgroundColor: 'transparent',
    color: convertedSeries.map((s) => s.color),
    legend: legendBase(palette, { data: convertedSeries.map((s) => s.name) }),
    grid: { left: 8, right: 12, top: 44, bottom: showZoom ? 92 : 40, containLabel: true },
    dataZoom: showZoom ? zoomBlock : [],
    tooltip: {
      trigger: 'axis',
      // Box hidden — the hovered point is shown in the non-overlapping ChartHoverReadout strip
      // below the chart. The axisPointer crosshair stays and updateAxisPointer still fires.
      showContent: false,
      axisPointer: { lineStyle: { color: palette.muted, opacity: 0.4 } },
    },
    xAxis: timeAxis(palette, {
      axisLabel: {
        color: palette.muted, fontSize: 10, hideOverlap: true,
        formatter: (val) => {
          const d = new Date(val);
          return `${d.getDate()} ${d.toLocaleString(i18n.t('common.localeTag'), { month: 'short' })}`;
        },
      },
      minInterval: 24 * 3600 * 1000,
    }),
    yAxis: valueAxis(palette, {
      scale: !flat,
      min: flat ? dataMin - pad : undefined,
      max: flat ? dataMax + pad : undefined,
      axisLabel: { color: palette.muted, fontSize: 10, formatter: (val) => money(val) },
    }),
    series: convertedSeries.map((s) => ({
      ...lineSeriesDefaults(s.color, s.data.length),
      name: s.name,
      data: s.data,
      emphasis: { focus: 'series' },
    })),
    media: [{
      query: { maxWidth: 640 },
      option: {
        grid: { left: 4, right: 8, top: 40, bottom: showZoom ? 80 : 32 },
        xAxis: { axisLabel: { fontSize: 9, rotate: 35 } },
        yAxis: { axisLabel: { fontSize: 9 } },
      },
    }],
  };
}

function PnlTimeSeriesChart({ series = [], loading = false, range, onRangeChange, title, Icon, headerExtra, emptyLabel, valueMode = 'money', showRange = true, showSummary = false, preConverted = false }) {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const { convertAt, currency } = useRateHistory();
  const chartRef = useRef(null);
  const isPercent = valueMode === 'percent';
  const [hoverTime, setHoverTime] = useState(null);

  const safeCurrency = currency === 'USD' || currency === 'EUR' ? currency : 'TRY';
  // Always formats a TRY amount as the display currency (used for the secondary "price"/value).
  const fmtMoney = useCallback((value) => {
    if (value == null) return '—';
    const abs = Math.abs(value);
    const maxDecimals = abs < 10 ? 4 : abs < 1000 ? 3 : 2;
    return formatPrice(value, { currency: safeCurrency, minDecimals: 2, maxDecimals });
  }, [safeCurrency]);
  // Mode-aware: percent in percent mode (no FX), currency otherwise. Drives the y-axis + plotted value.
  const money = useCallback((value) => {
    if (value == null) return '—';
    if (isPercent) return `${value >= 0 ? '+' : ''}${Number(value).toFixed(2)}%`;
    return fmtMoney(value);
  }, [isPercent, fmtMoney]);

  const palette = useMemo(() => themePalette(isDark), [isDark]);

  // Cap each series so the per-point FX conversion + hover stay cheap on a long history (no visual change).
  const cappedSeries = useMemo(() => series.map((s) => ({ ...s, points: capPoints(s.points) })), [series]);

  const convertedSeries = useMemo(() => cappedSeries.map((s) => ({
    name: s.name,
    color: s.color,
    data: (s.points || []).map((p) => {
      const dateStr = new Date(p.time).toLocaleDateString('sv-SE');
      // preConverted: the caller already converted each PnL component at its own date (value@date −
      // cost@entry-date); converting again here at one rate would re-introduce the FX-difference bug.
      const v = isPercent || preConverted ? p.valueTry : convertAt(p.valueTry, 'TRY', dateStr);
      return { value: [p.time, v] };
    }),
  })), [cappedSeries, convertAt, isPercent, preConverted]);

  const hasData = convertedSeries.some((s) => s.data.length > 0);
  const option = useMemo(
    () => (hasData ? buildOption(convertedSeries, palette, money) : null),
    [hasData, convertedSeries, palette, money]
  );

  const onEvents = useMemo(() => ({
    updateAxisPointer: (e) => setHoverTime(e?.axesInfo?.[0]?.value ?? null),
    globalout: () => setHoverTime(null),
  }), []);

  // Readout strip below the chart: per series, the value (and TL price) at the hovered time — or the
  // latest point when not hovering. Matches each series by time so lines of different length align.
  const readout = useMemo(() => {
    if (!hasData) return { date: '', fields: [] };
    let activeTime = hoverTime;
    if (activeTime == null) {
      for (const s of cappedSeries) {
        const last = s.points?.[s.points.length - 1];
        if (last && (activeTime == null || last.time > activeTime)) activeTime = last.time;
      }
    }
    if (activeTime == null) return { date: '', fields: [] };
    const date = new Date(activeTime).toLocaleDateString(i18n.t('common.localeTag'), { day: '2-digit', month: 'short', year: 'numeric' });
    const fields = cappedSeries.map((s, i) => {
      const p = nearestPoint(s.points, activeTime);
      if (!p) return null;
      const dateStr = new Date(p.time).toLocaleDateString('sv-SE');
      const plotted = money(isPercent || preConverted ? p.valueTry : convertAt(p.valueTry, 'TRY', dateStr));
      const price = p.priceTry != null ? fmtMoney(preConverted ? p.priceTry : convertAt(p.priceTry, 'TRY', dateStr)) : null;
      return {
        key: `s${i}`,
        label: s.name,
        dot: s.color,
        value: price ? `${plotted} · ${price}` : plotted,
        tone: Number(p.valueTry) >= 0 ? 'pos' : 'neg',
      };
    }).filter(Boolean);
    return { date, fields };
  }, [cappedSeries, hasData, hoverTime, isPercent, convertAt, money, fmtMoney, preConverted]);

  // Header status: the primary (first = Total) series at the HOVERED time, falling back to the latest
  // point when not hovering — so the prominent headline tracks the cursor instead of being a static
  // latest value (which is misleading while hovering an earlier point).
  const summary = useMemo(() => {
    if (!showSummary) return null;
    const s0 = cappedSeries[0];
    if (!s0?.points?.length) return null;
    const p = hoverTime != null ? nearestPoint(s0.points, hoverTime) : s0.points[s0.points.length - 1];
    if (!p) return null;
    const dateStr = new Date(p.time).toLocaleDateString('sv-SE');
    const val = money(isPercent || preConverted ? p.valueTry : convertAt(p.valueTry, 'TRY', dateStr));
    // In percent mode the value already IS the %, so don't append a duplicate pct.
    const pct = (!isPercent && p.pct != null)
      ? ` (${p.pct >= 0 ? '+' : ''}${Number(p.pct).toFixed(2)}%)`
      : '';
    return { text: `${val}${pct}`, positive: Number(p.valueTry) >= 0 };
  }, [showSummary, cappedSeries, isPercent, money, convertAt, hoverTime, preConverted]);

  return (
    <motion.div variants={cardVariants} initial="hidden" animate="show">
      <Card variant="elevated" radius="2xl" padding="none" backdropBlur className="group">
        <div className="flex items-center justify-between p-4 sm:p-5 pb-0 gap-3 flex-wrap">
          <div className="flex items-center gap-3 min-w-0">
            {Icon && (
              <span className="flex items-center justify-center w-10 h-10 rounded-xl bg-accent/10">
                <Icon className="h-4.5 w-4.5 text-accent" />
              </span>
            )}
            <div className="min-w-0">
              <p className="text-sm font-bold text-fg">{title}</p>
              {summary && (
                <div className="mt-0.5">
                  {readout.date && (
                    <p className="text-[11px] font-mono text-fg-muted tabular-nums">{readout.date}</p>
                  )}
                  <span className={`block truncate text-lg font-mono font-bold tracking-tight ${summary.positive ? 'text-success' : 'text-danger'}`} title={summary.text}>
                    {summary.text}
                  </span>
                </div>
              )}
            </div>
          </div>
          {headerExtra}
        </div>

        {showRange && (
          <div className="flex items-center justify-end px-4 sm:px-5 pt-4 pb-2">
            <div className="max-w-full overflow-x-auto">
              <RangeSelector value={range} onChange={onRangeChange} layoutId="pnl-range" size="md" />
            </div>
          </div>
        )}

        <div className="relative min-h-[240px] sm:min-h-[360px] px-2">
          {loading ? (
            <div className="absolute inset-0 flex items-center justify-center">
              <Spinner size="md" tone="accent" />
            </div>
          ) : option ? (
            <ReactECharts
              ref={chartRef}
              key={`pnl-${isDark}-${currency}-${valueMode}`}
              option={option}
              notMerge
              lazyUpdate
              onEvents={onEvents}
              style={{ height: 'min(52vh, 360px)', minHeight: 240, width: '100%' }}
              opts={{ renderer: 'canvas' }}
            />
          ) : (
            <div className="flex flex-col items-center justify-center h-[240px] sm:h-[360px] gap-3">
              <p className="text-sm text-fg-muted">{emptyLabel || t('portfolio.pnlBreakdown.empty')}</p>
            </div>
          )}
        </div>
        {option && <ChartHoverReadout date={readout.date} fields={readout.fields} />}
      </Card>
    </motion.div>
  );
}

export default PnlTimeSeriesChart;
