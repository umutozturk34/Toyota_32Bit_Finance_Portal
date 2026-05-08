import { useMemo } from 'react';
import useSessionState from '../../../shared/hooks/useSessionState';
import useChartRange from '../../../shared/hooks/useChartRange';
import ReactECharts from 'echarts-for-react';
import { TrendingUp, Loader2 } from '../../../shared/components/feedback/AnimatedIcons';
import { usePortfolioPerformance, useBackfillStatus } from '../hooks/usePortfolioData';
import { formatPriceTRY } from '../../../shared/utils/formatters';
import { cardVariants } from '../../../shared/utils/animations';
import { useTheme } from '../../../shared/context/ThemeContext';
import useElapsedSeconds from '../../../shared/hooks/useElapsedSeconds';
import RangeSelector from '../../../shared/components/form/RangeSelector';
import {
  ASSET_TYPE_FILTERS as ASSET_TYPES,
  ASSET_TYPE_COLORS,
  ASSET_TYPE_LABELS,
} from '../../../shared/constants/assetTypes';

const POSITION_EVENT_META = {
  POSITION_ADDED: { color: '#10b981', label: 'Lot Eklendi' },
};

function themePalette(isDark) {
  return isDark
    ? { bg: 'rgba(12,12,20,0.96)', fg: '#e2e2ea', muted: '#6b6b7a', border: 'rgba(255,255,255,0.08)', grid: 'rgba(255,255,255,0.05)' }
    : { bg: 'rgba(255,255,255,0.98)', fg: '#1a1a2e', muted: '#94a3b8', border: 'rgba(0,0,0,0.08)', grid: 'rgba(0,0,0,0.04)' };
}

function buildTooltipHtml(point, palette) {
  const { bg, fg, muted, border } = palette;
  const totalValue = point.amount ?? (Array.isArray(point.value) ? Number(point.value[1]) : Number(point.value));
  const date = new Date(point.time).toLocaleDateString('tr-TR', {
    day: '2-digit', month: 'short', year: 'numeric',
  });
  const pnlColor = point.pnl >= 0 ? '#10b981' : '#ef4444';
  const pnlPrefix = point.pnl >= 0 ? '+' : '';

  const detailRows = (point.details || []).map((d) => {
    const color = ASSET_TYPE_COLORS[d.assetType] || '#6366f1';
    const label = d.label !== d.assetType ? d.label : (ASSET_TYPE_LABELS[d.assetType] || d.label);
    const dColor = d.pnlTry >= 0 ? '#10b981' : '#ef4444';
    return `<div style="display:flex;align-items:center;justify-content:space-between;gap:12px;padding:4px 0">
      <div style="display:flex;align-items:center;gap:6px">
        <span style="width:6px;height:6px;border-radius:50%;background:${color};display:inline-block;flex-shrink:0"></span>
        <span style="font-size:11px;color:${fg};opacity:0.85">${label}</span>
      </div>
      <div style="display:flex;gap:8px;align-items:baseline">
        <span style="font-size:11px;font-family:ui-monospace,monospace;color:${fg}">${formatPriceTRY(d.valueTry)}</span>
        <span style="font-size:10px;font-family:ui-monospace,monospace;color:${dColor}">${d.pnlTry >= 0 ? '+' : ''}${formatPriceTRY(d.pnlTry)}</span>
      </div>
    </div>`;
  }).join('');
  const detailBlock = detailRows
    ? `<div style="border-top:1px solid ${border};margin-top:8px;padding-top:8px">${detailRows}</div>`
    : '';

  const lotEvents = (point.events || []).filter((e) => POSITION_EVENT_META[e.type]);
  const eventRows = lotEvents.map((ev) => {
    const meta = POSITION_EVENT_META[ev.type];
    const codeLabel = ev.assetCode || (ASSET_TYPE_LABELS[ev.assetType] || ev.assetType);
    return `<div style="display:flex;align-items:center;justify-content:space-between;gap:10px;padding:3px 0">
      <div style="display:flex;align-items:center;gap:5px">
        <span style="width:5px;height:5px;border-radius:50%;background:${meta.color};display:inline-block"></span>
        <span style="font-size:10px;font-weight:600;color:${meta.color}">${meta.label}</span>
        <span style="font-size:10px;color:${muted}">${codeLabel}</span>
      </div>
      <span style="font-size:10px;font-family:ui-monospace,monospace;color:${fg};opacity:0.8">${formatPriceTRY(ev.valueTry)}</span>
    </div>`;
  }).join('');
  const eventBlock = eventRows
    ? `<div style="border-top:1px solid ${border};margin-top:6px;padding-top:6px">
        <div style="font-size:9px;text-transform:uppercase;letter-spacing:0.8px;color:${muted};margin-bottom:4px">Pozisyon Hareketleri</div>
        ${eventRows}
      </div>`
    : '';

  return `<div style="padding:12px 16px;min-width:260px;background:${bg};border-radius:12px;border:1px solid ${border};box-shadow:0 8px 32px rgba(0,0,0,0.25)">
    <div style="font-size:10px;color:${muted};margin-bottom:8px;letter-spacing:0.3px">${date}</div>
    <div style="display:flex;justify-content:space-between;align-items:baseline;gap:16px">
      <span style="font-size:16px;font-weight:700;font-family:ui-monospace,monospace;color:${fg}">${formatPriceTRY(totalValue)}</span>
      <span style="font-size:11px;font-family:ui-monospace,monospace;color:${pnlColor};font-weight:600">${pnlPrefix}${formatPriceTRY(point.pnl)} (${pnlPrefix}${point.pnlPercent?.toFixed(2) ?? '0.00'}%)</span>
    </div>
    ${detailBlock}
    ${eventBlock}
  </div>`;
}

function buildEChartsOption(data, color, palette) {
  const seriesData = data.map((d) => ({
    value: [d.time, d.value],
    amount: d.value,
    pnl: d.pnl,
    pnlPercent: d.pnlPercent,
    details: d.details,
    events: d.events,
    time: d.time,
  }));

  const markPointData = data
    .filter((d) => (d.events || []).some((e) => POSITION_EVENT_META[e.type]))
    .map((d) => ({ coord: [d.time, d.value] }));

  const values = data.map((d) => d.value);
  const dataMin = Math.min(...values);
  const dataMax = Math.max(...values);
  const span = dataMax - dataMin;
  const padding = span > 0 ? span * 0.08 : dataMax * 0.05;

  return {
    backgroundColor: 'transparent',
    animation: data.length < 200,
    grid: { left: 70, right: 24, top: 16, bottom: 32, containLabel: false },
    dataZoom: [
      { type: 'inside', xAxisIndex: 0, filterMode: 'none', zoomOnMouseWheel: true, moveOnMouseMove: 'shift', moveOnMouseWheel: false },
    ],
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'transparent',
      borderWidth: 0,
      padding: 0,
      extraCssText: 'box-shadow:none;',
      formatter: (params) => {
        const point = params?.[0]?.data;
        return point ? buildTooltipHtml(point, palette) : '';
      },
    },
    xAxis: {
      type: 'time',
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: palette.muted, fontSize: 10 },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      min: Math.max(0, dataMin - padding),
      max: dataMax + padding,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: palette.muted,
        fontSize: 10,
        formatter: (val) => formatPriceTRY(val),
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

export default function PerformanceChart({ portfolioId }) {
  const { isDark } = useTheme();
  const [range, setRange] = useChartRange('portfolio-perf-range');
  const [activeType, setActiveType] = useSessionState('portfolio-perf-type', null);

  const { data: perfData = [], isLoading: loading } = usePortfolioPerformance(portfolioId, range, activeType);
  const backfill = useBackfillStatus(portfolioId);
  const backfillElapsed = useElapsedSeconds(backfill.since);

  const mainColor = activeType ? (ASSET_TYPE_COLORS[activeType] || '#6366f1') : '#6366f1';
  const palette = useMemo(() => themePalette(isDark), [isDark]);
  const option = useMemo(
    () => (perfData.length > 0 ? buildEChartsOption(perfData, mainColor, palette) : null),
    [perfData, mainColor, palette]
  );

  const currentValue = perfData.length > 0 ? perfData[perfData.length - 1] : null;
  const totalPnl = currentValue?.pnl ?? null;
  const totalPnlPercent = currentValue?.pnlPercent ?? null;
  const pnlPositive = totalPnl != null && totalPnl >= 0;

  return (
    <motion.div variants={cardVariants} initial="hidden" animate="show">
      <div className="group relative rounded-2xl border border-border-default bg-bg-elevated backdrop-blur-md overflow-hidden card-hover transition-all duration-300 hover:border-border-hover">
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
                {activeType
                  ? ASSET_TYPES.find((t) => t.id === activeType)?.label + ' Performansı'
                  : 'Portföy Performansı'}
              </p>
              {currentValue && (
                <div className="flex items-center gap-2.5 mt-0.5">
                  <span className="text-xl font-mono font-bold text-fg tracking-tight">{formatPriceTRY(currentValue.value)}</span>
                  {totalPnl != null && (
                    <span className={`inline-flex items-center gap-1 text-xs font-mono font-semibold px-2 py-0.5 rounded-md ${pnlPositive ? 'text-success bg-success/10' : 'text-danger bg-danger/10'}`}>
                      {pnlPositive ? '+' : ''}{formatPriceTRY(totalPnl)} ({totalPnlPercent?.toFixed(2)}%)
                    </span>
                  )}
                </div>
              )}
            </div>
          </div>

          <div className="flex items-center gap-3">
            {backfill.running && (
              <div className="flex items-center gap-1.5 text-[10px] font-mono tracking-tight text-accent/90">
                <Loader2 className="h-2.5 w-2.5 animate-spin" />
                <span>hesaplanıyor · {String(backfillElapsed).padStart(2, '0')}s</span>
              </div>
            )}
            <div className="flex items-center gap-1.5">
              <span className="relative w-2 h-2">
                <span className="absolute inset-0 rounded-full bg-success animate-ping opacity-30" />
                <span className="relative block w-2 h-2 rounded-full bg-success" />
              </span>
              <span className="text-[10px] text-fg-muted font-medium">Lot Eklendi</span>
            </div>
          </div>
        </div>

        <div className="flex items-center justify-between px-5 pt-4 pb-2 gap-2 flex-wrap">
          <div className="inline-flex gap-0.5 rounded-xl border border-border-default bg-bg-base p-1">
            {ASSET_TYPES.map(({ id, label }) => (
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
                  {label}
                </span>
              </button>
            ))}
          </div>
          <RangeSelector value={range} onChange={setRange} layoutId="perf-range" size="md" />
        </div>

        <div className="relative min-h-[380px] px-2">
          {loading ? (
            <div className="absolute inset-0 flex items-center justify-center">
              <Loader2 className="h-6 w-6 animate-spin text-accent" />
            </div>
          ) : option ? (
            <ReactECharts
              key={`${activeType}-${range}-${isDark}`}
              option={option}
              notMerge
              lazyUpdate
              style={{ height: 380 }}
              opts={{ renderer: 'canvas' }}
            />
          ) : (
            <div className="flex flex-col items-center justify-center h-[380px] gap-3">
              <TrendingUp className="h-8 w-8 text-fg-subtle" />
              <p className="text-sm text-fg-muted">Bu aralıkta veri bulunmuyor</p>
            </div>
          )}
        </div>
      </div>
    </motion.div>
  );
}
