import { useCallback, useMemo } from 'react';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import useSessionState from '../../../shared/hooks/useSessionState';
import useChartRange from '../../../shared/hooks/useChartRange';
import ReactECharts from 'echarts-for-react';
import { TrendingUp } from '../../../shared/components/feedback/AnimatedIcons';
import { usePortfolioPerformance, useBackfillStatus } from '../hooks/usePortfolioData';
import { useRateHistory } from '../../../shared/hooks/useRateHistory';
import { formatPrice } from '../../../shared/utils/formatters';
import { cardVariants } from '../../../shared/utils/animations';
import Card from '../../../shared/components/card';
import Spinner from '../../../shared/components/feedback/Spinner';
import { useTheme } from '../../../shared/context/useTheme';
import useElapsedSeconds from '../../../shared/hooks/useElapsedSeconds';
import RangeSelector from '../../../shared/components/form/RangeSelector';
import i18n from '../../../shared/i18n/config';
import {
  ASSET_TYPE_FILTERS as ASSET_TYPES,
  ASSET_TYPE_COLORS,
} from '../../../shared/constants/assetTypes';

const POSITION_EVENT_META = {
  POSITION_ADDED: { color: '#10b981', labelKey: 'portfolio.performance.lotAdded' },
  POSITION_SOLD: { color: '#ef4444', labelKey: 'portfolio.performance.lotSold' },
};

function themePalette(isDark) {
  return isDark
    ? { bg: 'rgba(12,12,20,0.96)', fg: '#e2e2ea', muted: '#6b6b7a', border: 'rgba(255,255,255,0.08)', grid: 'rgba(255,255,255,0.05)' }
    : { bg: 'rgba(255,255,255,0.98)', fg: '#1a1a2e', muted: '#94a3b8', border: 'rgba(0,0,0,0.08)', grid: 'rgba(0,0,0,0.04)' };
}

function buildTooltipHtml(point, palette, money) {
  const { bg, fg, muted, border } = palette;
  const totalValue = point.amount ?? (Array.isArray(point.value) ? Number(point.value[1]) : Number(point.value));
  const localeTag = i18n.t('common.localeTag');
  const date = new Date(point.time).toLocaleDateString(localeTag, {
    day: '2-digit', month: 'short', year: 'numeric',
  });
  const pnlColor = point.pnl >= 0 ? '#10b981' : '#ef4444';
  const pnlPrefix = point.pnl >= 0 ? '+' : '';

  const detailRows = (point.details || []).map((d) => {
    const isOther = d.label === 'OTHER';
    const color = isOther ? '#7d8590' : (ASSET_TYPE_COLORS[d.assetType] || '#6366f1');
    const label = isOther
      ? i18n.t('portfolio.allocation.otherLabel')
      : d.label !== d.assetType ? d.label : i18n.t(`assets.labels.${d.assetType}`, { defaultValue: d.assetType });
    const dColor = d.pnlTry >= 0 ? '#10b981' : '#ef4444';
    return `<div style="display:flex;align-items:center;justify-content:space-between;gap:12px;padding:4px 0">
      <div style="display:flex;align-items:center;gap:6px">
        <span style="width:6px;height:6px;border-radius:50%;background:${color};display:inline-block;flex-shrink:0"></span>
        <span style="font-size:11px;color:${fg};opacity:0.85">${label}</span>
      </div>
      <div style="display:flex;gap:8px;align-items:baseline">
        <span style="font-size:11px;font-family:ui-monospace,monospace;color:${fg}">${money(d.valueTry)}</span>
        <span style="font-size:10px;font-family:ui-monospace,monospace;color:${dColor}">${d.pnlTry >= 0 ? '+' : ''}${money(d.pnlTry)}</span>
      </div>
    </div>`;
  }).join('');
  const cashRow = point.cash != null && point.cash !== 0
    ? `<div style="display:flex;align-items:center;justify-content:space-between;gap:12px;padding:4px 0">
        <div style="display:flex;align-items:center;gap:6px">
          <span style="width:6px;height:6px;border-radius:50%;background:#94a3b8;display:inline-block;flex-shrink:0"></span>
          <span style="font-size:11px;color:${fg};opacity:0.85">${i18n.t('portfolio.performance.realizedPnlLabel', { defaultValue: 'Realized P/L' })}</span>
        </div>
        <span style="font-size:11px;font-family:ui-monospace,monospace;color:${point.cash < 0 ? '#ef4444' : fg}">${money(point.cash)}</span>
      </div>`
    : '';
  const detailBlock = (detailRows || cashRow)
    ? `<div style="border-top:1px solid ${border};margin-top:8px;padding-top:8px">${detailRows}${cashRow}</div>`
    : '';

  const lotEvents = (point.events || []).filter((e) => POSITION_EVENT_META[e.type]);
  const eventRows = lotEvents.map((ev) => {
    const meta = POSITION_EVENT_META[ev.type];
    const codeLabel = ev.assetCode || i18n.t(`assets.labels.${ev.assetType}`, { defaultValue: ev.assetType });
    const qty = ev.quantity != null ? Number(ev.quantity) : null;
    const qtyText = qty != null && qty > 0
      ? `<span style="font-size:10px;font-family:ui-monospace,monospace;color:${muted}">×${qty.toLocaleString(localeTag, { maximumFractionDigits: 8 })}</span>`
      : '';
    return `<div style="display:flex;align-items:center;justify-content:space-between;gap:10px;padding:3px 0">
      <div style="display:flex;align-items:center;gap:5px">
        <span style="width:5px;height:5px;border-radius:50%;background:${meta.color};display:inline-block"></span>
        <span style="font-size:10px;font-weight:600;color:${meta.color}">${i18n.t(meta.labelKey)}</span>
        <span style="font-size:10px;color:${muted}">${codeLabel}</span>
        ${qtyText}
      </div>
      <span style="font-size:10px;font-family:ui-monospace,monospace;color:${fg};opacity:0.8">${money(ev.valueTry)}</span>
    </div>`;
  }).join('');
  const eventBlock = eventRows
    ? `<div style="border-top:1px solid ${border};margin-top:6px;padding-top:6px">
        <div style="font-size:9px;text-transform:uppercase;letter-spacing:0.8px;color:${muted};margin-bottom:4px">${i18n.t('portfolio.performance.positionEvents')}</div>
        ${eventRows}
      </div>`
    : '';

  return `<div style="padding:12px 16px;min-width:260px;background:${bg};border-radius:12px;border:1px solid ${border};box-shadow:0 8px 32px rgba(0,0,0,0.25)">
    <div style="font-size:10px;color:${muted};margin-bottom:8px;letter-spacing:0.3px">${date}</div>
    <div style="display:flex;justify-content:space-between;align-items:baseline;gap:16px">
      <span style="font-size:16px;font-weight:700;font-family:ui-monospace,monospace;color:${fg}">${money(totalValue)}</span>
      <span style="font-size:11px;font-family:ui-monospace,monospace;color:${pnlColor};font-weight:600">${pnlPrefix}${money(point.pnl)} (${pnlPrefix}${point.pnlPercent?.toFixed(2) ?? '0.00'}%)</span>
    </div>
    ${detailBlock}
    ${eventBlock}
  </div>`;
}

function buildEChartsOption(data, color, palette, money) {
  const seriesData = data.map((d) => ({
    value: [d.time, d.value],
    amount: d.value,
    cash: d.cash,
    pnl: d.pnl,
    pnlPercent: d.pnlPercent,
    details: d.details,
    events: d.events,
    time: d.time,
  }));

  const markPointData = data
    .filter((d) => (d.events || []).some((e) => POSITION_EVENT_META[e.type]))
    .map((d) => {
      const evs = (d.events || []).filter((e) => POSITION_EVENT_META[e.type]);
      const hasSold = evs.some((e) => e.type === 'POSITION_SOLD');
      const hasAdded = evs.some((e) => e.type === 'POSITION_ADDED');
      const color = hasSold && hasAdded
        ? '#f59e0b'
        : hasSold ? '#ef4444' : '#10b981';
      return {
        coord: [d.time, d.value],
        itemStyle: {
          color,
          borderColor: color === '#ef4444' ? '#1f0a0a' : color === '#f59e0b' ? '#2a1a05' : '#0a1f17',
          borderWidth: 2,
          shadowColor: color + '99',
          shadowBlur: 8,
        },
      };
    });

  const values = data.map((d) => d.value);
  const dataMin = Math.min(...values);
  const dataMax = Math.max(...values);
  const span = dataMax - dataMin;
  const padding = span > 0 ? span * 0.08 : dataMax * 0.05;

  const showZoom = data.length >= 2;
  return {
    backgroundColor: 'transparent',
    animation: data.length < 200,
    grid: { left: 70, right: 24, top: 16, bottom: showZoom ? 92 : 40, containLabel: false },
    dataZoom: showZoom ? [
      {
        type: 'inside',
        filterMode: 'none',
        zoomOnMouseWheel: true,
        moveOnMouseMove: true,
        moveOnMouseWheel: false,
        preventDefaultMouseMove: true,
      },
      {
        type: 'slider',
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
      confine: true,
      backgroundColor: 'transparent',
      borderWidth: 0,
      padding: 0,
      extraCssText: 'box-shadow:none;',
      formatter: (params) => {
        const point = params?.[0]?.data;
        return point ? buildTooltipHtml(point, palette, money) : '';
      },
    },
    xAxis: {
      type: 'time',
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: palette.muted, fontSize: 10,
        hideOverlap: true,
        formatter: (val) => {
          const d = new Date(val);
          return `${d.getDate()} ${d.toLocaleString(i18n.t('common.localeTag'), { month: 'short' })}`;
        },
      },
      minInterval: 24 * 3600 * 1000,
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      min: dataMin - padding,
      max: dataMax + padding,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: palette.muted,
        fontSize: 10,
        formatter: (val) => money(val),
      },
      splitLine: { lineStyle: { color: palette.grid, type: 'dashed' } },
    },
    series: [{
      type: 'line',
      smooth: data.length < 200,
      showSymbol: false,
      sampling: 'lttb',
      data: seriesData,
      itemStyle: { color },
      lineStyle: { width: 2, color },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: color + '55' },
            { offset: 1, color: color + '00' },
          ],
        },
      },
      markPoint: {
        symbol: 'circle',
        symbolSize: 12,
        itemStyle: {
          color: '#10b981',
          borderColor: '#0a1f17',
          borderWidth: 2,
          shadowColor: 'rgba(16, 185, 129, 0.6)',
          shadowBlur: 8,
        },
        label: { show: false },
        emphasis: {
          scale: 1.3,
          label: { show: false },
        },
        animation: false,
        data: markPointData,
      },
      emphasis: { focus: 'series' },
    }],
  };
}

export default function PerformanceChart({ portfolioId, backfill: backfillProp }) {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const { convertAt, currency } = useRateHistory();
  const [range, setRange] = useChartRange();
  const [activeType, setActiveType] = useSessionState('portfolio-perf-type', null);

  const { data: perfData = [], isLoading, isFetching } = usePortfolioPerformance(portfolioId, range, activeType);
  const loading = isLoading || isFetching;
  const ownBackfill = useBackfillStatus(backfillProp ? null : portfolioId);
  const backfill = backfillProp ?? ownBackfill;
  const backfillElapsed = useElapsedSeconds(backfill.since);

  // Portfolio aggregates are TRY-canonical; for ORIGINAL preference the chart still shows TRY
  // (mixing per-asset natural currencies in a portfolio total makes no sense). Any non-ISO code
  // would throw a RangeError in Intl.NumberFormat — guard with a SUPPORTED fallback.
  const safeCurrency = currency === 'USD' || currency === 'EUR' ? currency : 'TRY';
  const money = useCallback((value) => {
    if (value == null) return 'N/A';
    const abs = Math.abs(value);
    const maxDecimals = abs < 10 ? 4 : abs < 1000 ? 3 : 2;
    return formatPrice(value, { currency: safeCurrency, minDecimals: 2, maxDecimals });
  }, [safeCurrency]);

  const convertedPerfData = useMemo(() => perfData.map((point) => {
    const dateStr = new Date(point.time).toISOString().slice(0, 10);
    return {
      ...point,
      value: convertAt(point.value, 'TRY', dateStr),
      pnl: convertAt(point.pnl, 'TRY', dateStr),
      cash: convertAt(point.cash, 'TRY', dateStr),
      details: (point.details || []).map((d) => ({
        ...d,
        valueTry: convertAt(d.valueTry, 'TRY', dateStr),
        pnlTry: convertAt(d.pnlTry, 'TRY', dateStr),
      })),
      events: (point.events || []).map((e) => ({
        ...e,
        valueTry: convertAt(e.valueTry, 'TRY', dateStr),
      })),
    };
  }), [perfData, convertAt]);

  const currentValue = convertedPerfData.length > 0 ? convertedPerfData[convertedPerfData.length - 1] : null;
  const isRealized = activeType === 'CASH';
  const currentRealized = currentValue?.value ?? 0;
  const mainColor = isRealized
    ? (currentRealized < 0 ? '#ef4444' : '#10b981')
    : (activeType ? (ASSET_TYPE_COLORS[activeType] || '#6366f1') : '#6366f1');
  const palette = useMemo(() => themePalette(isDark), [isDark]);
  const option = useMemo(
    () => (convertedPerfData.length > 0 ? buildEChartsOption(convertedPerfData, mainColor, palette, money) : null),
    [convertedPerfData, mainColor, palette, money]
  );

  const totalPnl = currentValue?.pnl ?? null;
  const totalPnlPercent = currentValue?.pnlPercent ?? null;
  const pnlPositive = isRealized
    ? currentRealized >= 0
    : totalPnl != null && totalPnl >= 0;

  return (
    <motion.div variants={cardVariants} initial="hidden" animate="show">
      <Card variant="elevated" radius="2xl" padding="none" backdropBlur className="group">
        <div
          className="pointer-events-none absolute -top-20 -left-20 w-44 h-44 rounded-full blur-[80px] opacity-0 group-hover:opacity-60 transition-opacity duration-500"
          style={{ background: `radial-gradient(circle, ${mainColor}20 0%, transparent 70%)` }}
        />

        <div className="flex items-center justify-between p-5 pb-0">
          <div className="flex items-center gap-3">
            <span className="flex items-center justify-center w-10 h-10 rounded-xl transition-transform duration-300 group-hover:scale-105" style={{ backgroundColor: mainColor + '15', boxShadow: `0 0 20px ${mainColor}10` }}>
              <TrendingUp className="h-4.5 w-4.5 text-accent" />
            </span>
            <div>
              <p className="text-sm font-bold text-fg">
                {isRealized
                  ? t('portfolio.performance.realizedPnlTitle')
                  : activeType
                    ? t('portfolio.performance.titleByType', { type: t(`assets.labels.${activeType}`, { defaultValue: activeType }) })
                    : t('portfolio.performance.title')}
              </p>
              {currentValue && (
                <div className="flex items-center gap-2.5 mt-0.5">
                  <span className="text-xl font-mono font-bold text-fg tracking-tight">{money(currentValue.value)}</span>
                  {totalPnl != null && (
                    <span className={`inline-flex items-center gap-1 text-xs font-mono font-semibold px-2 py-0.5 rounded-md ${pnlPositive ? 'text-success bg-success/10' : 'text-danger bg-danger/10'}`}>
                      {pnlPositive ? '+' : ''}{money(totalPnl)} ({totalPnlPercent?.toFixed(2)}%)
                    </span>
                  )}
                </div>
              )}
            </div>
          </div>

          <div className="flex items-center gap-3">
            {backfill.running && (
              <div className="flex items-center gap-1.5 text-[10px] font-mono tracking-tight text-accent/90">
                <Spinner size="xs" tone="inherit" />
                <span>{t('portfolio.performance.calculating')} · {String(backfillElapsed).padStart(2, '0')}s</span>
              </div>
            )}
            <div className="flex items-center gap-3">
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

        <div className="flex items-center justify-between px-5 pt-4 pb-2 gap-2 flex-wrap">
          <div className="inline-flex gap-0.5 rounded-xl border border-border-default bg-bg-base p-1">
            {[...ASSET_TYPES, { id: 'CASH' }].map(({ id }) => (
              <button
                key={id || 'all'}
                onClick={() => setActiveType(id)}
                className="relative rounded-lg px-3 py-1.5 text-[11px] font-semibold transition-all border-none cursor-pointer bg-transparent"
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
          <RangeSelector value={range} onChange={setRange} layoutId="perf-range" size="md" />
        </div>

        <div className="relative min-h-[420px] px-2">
          {loading ? (
            <div className="absolute inset-0 flex items-center justify-center">
              <Spinner size="md" tone="accent" />
            </div>
          ) : option ? (
            <ReactECharts
              key={`${activeType}-${range}-${isDark}-${currency}`}
              option={option}
              notMerge
              lazyUpdate
              style={{ height: 420 }}
              opts={{ renderer: 'canvas' }}
            />
          ) : (
            <div className="flex flex-col items-center justify-center h-[420px] gap-3">
              <TrendingUp className="h-8 w-8 text-fg-subtle" />
              <p className="text-sm text-fg-muted">{t('portfolio.performance.empty')}</p>
            </div>
          )}
        </div>
      </Card>
    </motion.div>
  );
}
