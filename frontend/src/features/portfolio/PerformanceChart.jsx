import { useState, useEffect, useCallback, useMemo } from 'react';
import { motion } from 'framer-motion';
import { TrendingUp, Loader2 } from 'lucide-react';
import Chart from 'react-apexcharts';
import { portfolioService } from './portfolioService';
import { formatPriceTRY } from '../../shared/utils/formatters';
import { cardVariants } from '../../shared/utils/animations';
import { useTheme } from '../../shared/context/ThemeContext';
import { getApexThemeOptions } from '../../shared/utils/apexTheme';

const RANGES = [
  { id: '1M', label: '1A' },
  { id: '3M', label: '3A' },
  { id: '6M', label: '6A' },
  { id: '1Y', label: '1Y' },
  { id: 'ALL', label: 'Tümü' },
];

const ASSET_TYPES = [
  { id: null, label: 'Tümü' },
  { id: 'CRYPTO', label: 'Kripto' },
  { id: 'STOCK', label: 'Hisse' },
  { id: 'FOREX', label: 'Döviz' },
  { id: 'FUND', label: 'Fon' },
];

const ASSET_TYPE_COLORS = {
  CRYPTO: '#f59e0b',
  STOCK: '#10b981',
  FOREX: '#06b6d4',
  FUND: '#8b5cf6',
};

const ASSET_TYPE_LABELS = {
  CRYPTO: 'Kripto',
  STOCK: 'Hisse',
  FOREX: 'Döviz',
  FUND: 'Fon',
};

const EVENT_CONFIG = {
  BUY: { label: 'Alış', color: '#10b981', icon: '▲' },
  SELL: { label: 'Satış', color: '#ef4444', icon: '▼' },
  MARKET_UP: { label: 'Fiyat Artışı', color: '#10b981', icon: '↑' },
  MARKET_DOWN: { label: 'Fiyat Düşüşü', color: '#ef4444', icon: '↓' },
};

function buildEventRows(events) {
  if (!events || events.length === 0) return '';
  const rows = events.map(ev => {
    const cfg = EVENT_CONFIG[ev.type] || { label: ev.type, color: '#6366f1', icon: '•' };
    const assetLabel = ASSET_TYPE_LABELS[ev.assetType] || ev.assetType;
    const codeLabel = ev.assetCode ? `${ev.assetCode}` : assetLabel;
    return `<div style="display:flex;align-items:center;justify-content:space-between;gap:10px;padding:2px 0">
      <div style="display:flex;align-items:center;gap:5px">
        <span style="color:${cfg.color};font-size:11px;font-weight:700;width:12px;text-align:center">${cfg.icon}</span>
        <span style="font-size:10px;font-weight:600;color:${cfg.color}">${cfg.label}</span>
        <span style="font-size:10px;opacity:0.7">${codeLabel}</span>
      </div>
      <span style="font-size:10px;font-family:monospace;opacity:0.85">${formatPriceTRY(ev.valueTry)}</span>
    </div>`;
  }).join('');
  return `<div style="border-top:1px solid rgba(128,128,128,0.25);margin-top:6px;padding-top:5px">
    <div style="font-size:9px;text-transform:uppercase;letter-spacing:0.5px;opacity:0.45;margin-bottom:3px">Olaylar</div>
    ${rows}
  </div>`;
}

function buildCustomTooltip(dataPoints) {
  return function ({ dataPointIndex }) {
    const point = dataPoints[dataPointIndex];
    if (!point) return '';

    const date = new Date(point.time).toLocaleDateString('tr-TR', {
      day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
    });

    const pnlColor = point.pnl >= 0 ? '#10b981' : '#ef4444';
    const pnlPrefix = point.pnl >= 0 ? '+' : '';

    let detailRows = '';
    if (point.details && point.details.length > 0) {
      detailRows = point.details.map(d => {
        const color = ASSET_TYPE_COLORS[d.assetType] || '#6366f1';
        const label = ASSET_TYPE_LABELS[d.assetType] || d.label;
        const displayLabel = d.label !== d.assetType ? d.label : label;
        const dPnlColor = d.pnlTry >= 0 ? '#10b981' : '#ef4444';
        const dPnlPrefix = d.pnlTry >= 0 ? '+' : '';
        return `<div style="display:flex;align-items:center;justify-content:space-between;gap:12px;padding:3px 0">
          <div style="display:flex;align-items:center;gap:6px">
            <span style="width:8px;height:8px;border-radius:50%;background:${color};display:inline-block"></span>
            <span style="font-size:11px;opacity:0.85">${displayLabel}</span>
          </div>
          <div style="display:flex;gap:8px;align-items:center">
            <span style="font-size:11px;font-family:monospace">${formatPriceTRY(d.valueTry)}</span>
            <span style="font-size:10px;font-family:monospace;color:${dPnlColor}">${dPnlPrefix}${formatPriceTRY(d.pnlTry)}</span>
          </div>
        </div>`;
      }).join('');
      detailRows = `<div style="border-top:1px solid rgba(128,128,128,0.2);margin-top:6px;padding-top:6px">${detailRows}</div>`;
    }

    const eventRows = buildEventRows(point.events);

    return `<div style="padding:10px 14px;min-width:280px">
      <div style="font-size:10px;opacity:0.6;margin-bottom:6px">${date}</div>
      <div style="display:flex;justify-content:space-between;align-items:baseline;gap:16px">
        <span style="font-size:14px;font-weight:600;font-family:monospace">${formatPriceTRY(point.value)}</span>
        <span style="font-size:11px;font-family:monospace;color:${pnlColor}">${pnlPrefix}${formatPriceTRY(point.pnl)} (${pnlPrefix}${point.pnlPercent?.toFixed(2) ?? '0.00'}%)</span>
      </div>
      ${detailRows}
      ${eventRows}
    </div>`;
  };
}

function buildAnnotations(data) {
  const points = [];
  data.forEach((d, idx) => {
    if (!d.events || d.events.length === 0) return;
    const hasTx = d.events.some(e => e.type === 'BUY' || e.type === 'SELL');
    if (!hasTx) return;

    const isBuy = d.events.some(e => e.type === 'BUY');
    const isSell = d.events.some(e => e.type === 'SELL');
    let markerColor = '#6366f1';
    let label = 'İşlem';
    if (isBuy && !isSell) { markerColor = '#10b981'; label = 'Alış'; }
    else if (isSell && !isBuy) { markerColor = '#ef4444'; label = 'Satış'; }
    else if (isBuy && isSell) { markerColor = '#f59e0b'; label = 'A/S'; }

    points.push({
      x: d.time,
      y: d.value,
      marker: {
        size: 5,
        fillColor: markerColor,
        strokeColor: '#fff',
        strokeWidth: 2,
        shape: 'circle',
      },
      label: {
        text: label,
        borderColor: markerColor,
        style: {
          color: '#fff',
          background: markerColor,
          fontSize: '9px',
          fontWeight: 600,
          padding: { left: 5, right: 5, top: 2, bottom: 2 },
        },
        offsetY: -10,
      },
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
      height: 360,
      toolbar: { show: false },
      zoom: { enabled: true, type: 'x', autoScaleYaxis: true },
      animations: { enabled: true, easing: 'easeinout', speed: 600 },
      redrawOnParentResize: true,
    },
    colors: [color],
    stroke: { curve: 'smooth', width: 2.5 },
    fill: {
      type: 'gradient',
      gradient: {
        shadeIntensity: 1,
        opacityFrom: 0.4,
        opacityTo: 0.02,
        stops: [0, 100],
      },
    },
    annotations: {
      points: annotationPoints,
    },
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
      labels: {
        ...themeOpts.yaxis.labels,
        formatter: (val) => formatPriceTRY(val),
      },
    },
    tooltip: {
      ...themeOpts.tooltip,
      custom: buildCustomTooltip(data),
    },
    grid: themeOpts.grid,
    dataLabels: { enabled: false },
  };

  const series = [{ name: 'Portföy Değeri', data: seriesData }];

  return <Chart options={options} series={series} type="area" height={360} />;
}

export default function PerformanceChart({ portfolioId }) {
  const { isDark } = useTheme();
  const [range, setRange] = useState('ALL');
  const [activeType, setActiveType] = useState(null);
  const [perfData, setPerfData] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchPerformance = useCallback(async (r, typeFilter) => {
    setLoading(true);
    try {
      const perf = await portfolioService.getPerformance(portfolioId, r, typeFilter);
      setPerfData((perf || []).map((d) => ({
        time: new Date(d.timestamp).getTime(),
        value: Number(d.totalValueTry),
        pnl: Number(d.totalPnlTry),
        pnlPercent: Number(d.pnlPercent),
        details: d.details || [],
        events: d.events || [],
      })));
    } catch {
      setPerfData([]);
    } finally {
      setLoading(false);
    }
  }, [portfolioId]);

  useEffect(() => { fetchPerformance(range, activeType); }, [range, activeType, fetchPerformance]);

  const mainColor = activeType ? (ASSET_TYPE_COLORS[activeType] || '#6366f1') : '#6366f1';

  return (
    <motion.div variants={cardVariants} initial="hidden" animate="show" className="space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <div className="flex items-center gap-2">
          <TrendingUp className="h-4 w-4 text-accent" />
          <span className="text-sm font-semibold text-fg">Performans</span>
        </div>
        <div className="flex gap-2 flex-wrap">
          <div className="flex gap-0.5 rounded-lg border border-border-default bg-bg-elevated p-0.5">
            {ASSET_TYPES.map(({ id, label }) => (
              <button
                key={id || 'all'}
                onClick={() => setActiveType(id)}
                className="relative rounded-md px-2.5 py-1 text-[11px] font-medium transition-all border-none cursor-pointer bg-transparent"
              >
                {activeType === id && (
                  <motion.span
                    layoutId="perf-type"
                    className="absolute inset-0 rounded-md bg-accent/15"
                    transition={{ type: 'spring', stiffness: 300, damping: 30 }}
                  />
                )}
                <span className={`relative z-10 ${activeType === id ? 'text-accent' : 'text-fg-muted hover:text-fg'}`}>
                  {label}
                </span>
              </button>
            ))}
          </div>
          <div className="flex gap-0.5 rounded-lg border border-border-default bg-bg-elevated p-0.5">
            {RANGES.map(({ id, label }) => (
              <button
                key={id}
                onClick={() => setRange(id)}
                className="relative rounded-md px-2.5 py-1 text-[11px] font-medium transition-all border-none cursor-pointer bg-transparent"
              >
                {range === id && (
                  <motion.span
                    layoutId="perf-range"
                    className="absolute inset-0 rounded-md bg-accent/15"
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
      </div>

      <div className="rounded-2xl border border-border-default bg-bg-elevated backdrop-blur-md p-5 space-y-2 card-hover transition-all duration-200 hover:border-border-hover">
        <div className="flex items-center justify-between">
          <span className="text-xs font-semibold text-fg">
            {activeType
              ? ASSET_TYPES.find(t => t.id === activeType)?.label + ' Değeri'
              : 'Toplam Portföy Değeri'}
          </span>
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-1">
              <span className="w-2 h-2 rounded-full bg-success inline-block"></span>
              <span className="text-[9px] text-fg-muted">Alış</span>
            </div>
            <div className="flex items-center gap-1">
              <span className="w-2 h-2 rounded-full bg-danger inline-block"></span>
              <span className="text-[9px] text-fg-muted">Satış</span>
            </div>
          </div>
        </div>
        <div className="relative min-h-[360px]">
          {loading ? (
            <div className="absolute inset-0 flex items-center justify-center">
              <Loader2 className="h-6 w-6 animate-spin text-accent" />
            </div>
          ) : perfData.length > 0 ? (
            <MainChart key={`${activeType}-${range}`} data={perfData} isDark={isDark} color={mainColor} />
          ) : (
            <div className="flex items-center justify-center h-[360px] text-sm text-fg-muted">
              Bu aralıkta veri bulunmuyor
            </div>
          )}
        </div>
      </div>
    </motion.div>
  );
}
