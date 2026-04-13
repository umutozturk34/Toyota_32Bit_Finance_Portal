import { useMemo } from 'react';
import useSessionState from "../../shared/hooks/useSessionState";
import { motion } from 'framer-motion';
import { TrendingUp, Loader2 } from '../../shared/components/AnimatedIcons';
import Chart from 'react-apexcharts';
import { usePortfolioPerformance } from './usePortfolioData';
import { formatPriceTRY } from '../../shared/utils/formatters';
import { cardVariants } from '../../shared/utils/animations';
import { useTheme } from '../../shared/context/ThemeContext';
import { getApexThemeOptions } from '../../shared/utils/apexTheme';
import {
  PORTFOLIO_RANGES as RANGES,
  ASSET_TYPE_FILTERS as ASSET_TYPES,
  ASSET_TYPE_COLORS,
  ASSET_TYPE_LABELS,
} from '../../shared/constants/assetTypes';

function buildCustomTooltip(dataPoints, isDark) {
  const bg = isDark ? 'rgba(12,12,20,0.95)' : 'rgba(255,255,255,0.97)';
  const fg = isDark ? '#e2e2ea' : '#1a1a2e';
  const muted = isDark ? '#6b6b7a' : '#94a3b8';
  const border = isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.06)';

  return function ({ dataPointIndex }) {
    const point = dataPoints[dataPointIndex];
    if (!point) return '';

    const date = new Date(point.time).toLocaleDateString('tr-TR', {
      day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
    });

    const pnlColor = point.pnl >= 0 ? '#10b981' : '#ef4444';
    const pnlPrefix = point.pnl >= 0 ? '+' : '';

    let detailRows = '';
    if (point.details?.length > 0) {
      detailRows = point.details.map(d => {
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
      detailRows = `<div style="border-top:1px solid ${border};margin-top:8px;padding-top:8px">${detailRows}</div>`;
    }

    let eventRows = '';
    const txEvents = (point.events || []).filter(e => e.type === 'BUY' || e.type === 'SELL');
    if (txEvents.length > 0) {
      const rows = txEvents.map(ev => {
        const isBuy = ev.type === 'BUY';
        const evColor = isBuy ? '#10b981' : '#ef4444';
        const evLabel = isBuy ? 'Alış' : 'Satış';
        const codeLabel = ev.assetCode || (ASSET_TYPE_LABELS[ev.assetType] || ev.assetType);
        return `<div style="display:flex;align-items:center;justify-content:space-between;gap:10px;padding:3px 0">
          <div style="display:flex;align-items:center;gap:5px">
            <span style="width:5px;height:5px;border-radius:50%;background:${evColor};display:inline-block"></span>
            <span style="font-size:10px;font-weight:600;color:${evColor}">${evLabel}</span>
            <span style="font-size:10px;color:${muted}">${codeLabel}</span>
          </div>
          <span style="font-size:10px;font-family:ui-monospace,monospace;color:${fg};opacity:0.8">${formatPriceTRY(ev.valueTry)}</span>
        </div>`;
      }).join('');
      eventRows = `<div style="border-top:1px solid ${border};margin-top:6px;padding-top:6px">
        <div style="font-size:9px;text-transform:uppercase;letter-spacing:0.8px;color:${muted};margin-bottom:4px">İşlemler</div>
        ${rows}
      </div>`;
    }

    return `<div style="padding:12px 16px;min-width:260px;background:${bg};border-radius:12px;border:1px solid ${border};backdrop-filter:blur(12px);box-shadow:0 8px 32px rgba(0,0,0,0.2)">
      <div style="font-size:10px;color:${muted};margin-bottom:8px;letter-spacing:0.3px">${date}</div>
      <div style="display:flex;justify-content:space-between;align-items:baseline;gap:16px">
        <span style="font-size:16px;font-weight:700;font-family:ui-monospace,monospace;color:${fg}">${formatPriceTRY(point.value)}</span>
        <span style="font-size:11px;font-family:ui-monospace,monospace;color:${pnlColor};font-weight:600">${pnlPrefix}${formatPriceTRY(point.pnl)} (${pnlPrefix}${point.pnlPercent?.toFixed(2) ?? '0.00'}%)</span>
      </div>
      ${detailRows}
      ${eventRows}
    </div>`;
  };
}

function buildAnnotations(data) {
  const points = [];
  data.forEach((d) => {
    if (!d.events || d.events.length === 0) return;
    const hasTx = d.events.some(e => e.type === 'BUY' || e.type === 'SELL');
    if (!hasTx) return;

    const isBuy = d.events.some(e => e.type === 'BUY');
    const isSell = d.events.some(e => e.type === 'SELL');
    let markerColor = '#6366f1';
    if (isBuy && !isSell) markerColor = '#10b981';
    else if (isSell && !isBuy) markerColor = '#ef4444';
    else if (isBuy && isSell) markerColor = '#f59e0b';

    points.push({
      x: d.time,
      y: d.value,
      marker: {
        size: 4,
        fillColor: markerColor,
        strokeColor: markerColor + '30',
        strokeWidth: 6,
        shape: 'circle',
      },
      label: { text: '' },
    });
  });
  return points;
}

function MainChart({ data, isDark, color }) {
  if (!data || data.length === 0) return null;

  const values = data.map(d => d.value);
  const dataMin = Math.min(...values);
  const dataMax = Math.max(...values);
  const span = dataMax - dataMin;
  const padding = span > 0 ? span * 0.08 : dataMax * 0.05;
  const yMin = Math.max(0, dataMin - padding);
  const yMax = dataMax + padding;

  const annotationPoints = useMemo(() => buildAnnotations(data), [data]);
  const themeOpts = getApexThemeOptions(isDark);

  const seriesData = useMemo(() => data.map(d => ({ x: d.time, y: d.value })), [data]);

  const options = {
    ...themeOpts,
    chart: {
      ...themeOpts.chart,
      type: 'area',
      height: 380,
      toolbar: { show: false },
      zoom: { enabled: true, type: 'x', autoScaleYaxis: true },
      animations: { enabled: data.length < 100, easing: 'easeinout', speed: 400 },
      redrawOnParentResize: true,
    },
    colors: [color],
    stroke: { curve: data.length > 200 ? 'straight' : 'smooth', width: 2 },
    markers: { size: 0, hover: { size: 5, sizeOffset: 2 } },
    fill: {
      type: 'gradient',
      gradient: { shadeIntensity: 1, opacityFrom: 0.35, opacityTo: 0.0, stops: [0, 95] },
    },
    annotations: { points: annotationPoints },
    xaxis: {
      ...themeOpts.xaxis,
      type: 'datetime',
      tickAmount: Math.min(data.length, 8),
    },
    yaxis: {
      ...themeOpts.yaxis,
      min: yMin,
      max: yMax,
      forceNiceScale: false,
      tickAmount: 6,
      labels: { ...themeOpts.yaxis.labels, formatter: (val) => formatPriceTRY(val) },
    },
    tooltip: {
      ...themeOpts.tooltip,
      custom: buildCustomTooltip(data, isDark),
      intersect: false,
      shared: false,
    },
    grid: { ...themeOpts.grid, padding: { left: 12, right: 12, top: 0, bottom: 0 } },
    dataLabels: { enabled: false },
  };

  return <Chart options={options} series={[{ name: 'Portföy Değeri', data: seriesData }]} type="area" height={380} />;
}

export default function PerformanceChart({ portfolioId }) {
  const { isDark } = useTheme();
  const [range, setRange] = useSessionState('portfolio-perf-range', 'ALL');
  const [activeType, setActiveType] = useSessionState('portfolio-perf-type', null);

  const { data: perfData = [], isLoading: loading } = usePortfolioPerformance(portfolioId, range, activeType);

  const mainColor = activeType ? (ASSET_TYPE_COLORS[activeType] || '#6366f1') : '#6366f1';

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
                  ? ASSET_TYPES.find(t => t.id === activeType)?.label + ' Performansı'
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

          <div className="flex items-center gap-4">
            <div className="flex items-center gap-1.5">
              <span className="relative w-2 h-2">
                <span className="absolute inset-0 rounded-full bg-success animate-ping opacity-30" />
                <span className="relative block w-2 h-2 rounded-full bg-success" />
              </span>
              <span className="text-[10px] text-fg-muted font-medium">Alış</span>
            </div>
            <div className="flex items-center gap-1.5">
              <span className="relative w-2 h-2">
                <span className="absolute inset-0 rounded-full bg-danger animate-ping opacity-30" />
                <span className="relative block w-2 h-2 rounded-full bg-danger" />
              </span>
              <span className="text-[10px] text-fg-muted font-medium">Satış</span>
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
          <div className="inline-flex gap-0.5 rounded-xl border border-border-default bg-bg-base p-1">
            {RANGES.map(({ id, label }) => (
              <button
                key={id}
                onClick={() => setRange(id)}
                className="relative rounded-lg px-3 py-1.5 text-[11px] font-semibold transition-all border-none cursor-pointer bg-transparent"
              >
                {range === id && (
                  <motion.span
                    layoutId="perf-range"
                    className="absolute inset-0 rounded-lg bg-accent/15"
                    transition={{ type: 'spring', stiffness: 300, damping: 30 }}
                  />
                )}
                <span className={`relative z-10 ${range === id ? 'text-accent' : 'text-fg-muted hover:text-fg'}`}>
                  {label}
                </span>
              </button>
            ))}
          </div>
        </div>

        <div className="relative min-h-[380px] px-2">
          {loading ? (
            <div className="absolute inset-0 flex items-center justify-center">
              <Loader2 className="h-6 w-6 animate-spin text-accent" />
            </div>
          ) : perfData.length > 0 ? (
            <MainChart key={`${activeType}-${range}`} data={perfData} isDark={isDark} color={mainColor} />
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
