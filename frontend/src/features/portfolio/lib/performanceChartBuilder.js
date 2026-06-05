import i18n from '../../../shared/i18n/config';
import { ASSET_TYPE_COLORS } from '../../../shared/constants/assetTypes';
import {
  timeAxis,
  valueAxis,
  dataZoomBlock,
  lineSeriesDefaults,
  areaGradient,
} from '../../../shared/charts/echartsTheme';

// Pure ECharts/data utilities for the Performance chart — no React state. Extracted from
// PerformanceChart.jsx so the component stays a thin view over these builders (sibling to assetChartBuilder.js).

export const POSITION_EVENT_META = {
  POSITION_ADDED: { color: '#10b981', labelKey: 'portfolio.performance.lotAdded' },
  POSITION_SOLD: { color: '#ef4444', labelKey: 'portfolio.performance.lotSold' },
};

export function themePalette(isDark) {
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

// Cap a long series to ~max points (uniform stride, endpoints kept) so the per-date FX conversion and the
// hover scan stay cheap; ECharts' lttb sampling then fits it to pixel width. Mirrors PnlTimeSeriesChart — a
// multi-year daily history (~11k pts) × per-date USD conversion + an O(n) hover scan janked this chart.
export function capPoints(points, max = 1500) {
  if (!points || points.length <= max) return points || [];
  // Uniform stride for the flat regions, but ALWAYS keep event-bearing points (lot added/closed) so their
  // step + marker survive the cap. A cumulative realized series is mostly flat with a few event steps; a
  // blind uniform sample dropped the close-day point, so the step and its red marker "disappeared".
  const keep = new Set();
  const step = (points.length - 1) / (max - 1);
  for (let i = 0; i < max; i += 1) keep.add(Math.round(i * step));
  for (let i = 0; i < points.length; i += 1) {
    if (points[i] && (points[i].events || []).length > 0) keep.add(i);
  }
  return [...keep].sort((a, b) => a - b).map((i) => points[i]);
}

// Binary search for the INDEX of the point nearest time `t` (points time-ascending) — O(log n) instead of
// the O(n) scan that ran on every mouse-move. Returns null on empty.
export function nearestIndex(points, t) {
  if (!points || points.length === 0) return null;
  if (t == null) return points.length - 1;
  let lo = 0;
  let hi = points.length - 1;
  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (points[mid].time < t) lo = mid + 1; else hi = mid;
  }
  if (lo > 0 && Math.abs(points[lo - 1].time - t) <= Math.abs(points[lo].time - t)) return lo - 1;
  return lo;
}

export function buildEChartsOption(data, color, palette, money, forPrint = false) {
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
      const markColor = hasSold && hasAdded
        ? '#f59e0b'
        : hasSold ? '#ef4444' : '#10b981';
      return {
        coord: [d.time, d.value],
        itemStyle: {
          color: markColor,
          borderColor: markColor === '#ef4444' ? '#1f0a0a' : markColor === '#f59e0b' ? '#2a1a05' : '#0a1f17',
          borderWidth: 2,
          shadowColor: markColor + '99',
          shadowBlur: 8,
        },
      };
    });

  const values = data.map((d) => d.value);
  const dataMin = Math.min(...values);
  const dataMax = Math.max(...values);
  const span = dataMax - dataMin;
  // Auto-zoom (padding ~ span × 8%) magnifies a near-constant line — e.g. a closed lot whose value is the
  // same ~7797.67 every day, with only sub-cent FX-rounding jitter — into "dramatic" waves. When the span is
  // negligible vs |value| (or basically zero), floor the y-range to a fraction of |value| so a flat series
  // renders flat. Genuinely varying series (span above the epsilon) keep normal auto-scaling.
  const scale = Math.max(Math.abs(dataMax), Math.abs(dataMin));
  const flatEpsilon = Math.max(scale * 1e-4, 1e-6);
  const padding = span > flatEpsilon ? span * 0.08 : Math.max(scale * 0.05, 1);

  const showZoom = !forPrint && data.length >= 2;
  const zoomBlock = dataZoomBlock(palette, { filterMode: 'none', height: 26 });
  if (zoomBlock[0]) zoomBlock[0].filterMode = 'none';
  return {
    backgroundColor: 'transparent',
    animation: !forPrint && data.length < 200,
    grid: { left: 8, right: 12, top: 16, bottom: showZoom ? 92 : 40, containLabel: true },
    dataZoom: showZoom ? zoomBlock : [],
    tooltip: forPrint ? { show: false } : {
      trigger: 'axis',
      // Box hidden — the hovered point is shown in the non-overlapping ChartHoverReadout strip
      // below the chart. The axisPointer crosshair stays, and updateAxisPointer still fires.
      showContent: false,
      confine: true,
      position: (point, _params, _dom, _rect, size) => {
        const x = Math.max(8, Math.min(point[0] - size.contentSize[0] / 2, size.viewSize[0] - size.contentSize[0] - 8));
        return [x, 8];
      },
      backgroundColor: 'transparent',
      borderWidth: 0,
      padding: 0,
      extraCssText: 'box-shadow:none;',
      formatter: (params) => {
        const point = params?.[0]?.data;
        return point ? buildTooltipHtml(point, palette, money) : '';
      },
    },
    xAxis: timeAxis(palette, {
      axisLabel: {
        color: palette.muted, fontSize: 10,
        hideOverlap: true,
        formatter: (val) => {
          const d = new Date(val);
          return `${d.getDate()} ${d.toLocaleString(i18n.t('common.localeTag'), { month: 'short' })}`;
        },
      },
      minInterval: 24 * 3600 * 1000,
    }),
    yAxis: valueAxis(palette, {
      scale: false,
      min: dataMin - padding,
      max: dataMax + padding,
      axisLabel: { color: palette.muted, fontSize: 10, formatter: (val) => money(val) },
    }),
    series: [{
      ...lineSeriesDefaults(color, data.length),
      data: seriesData,
      areaStyle: { color: areaGradient(color) },
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
    media: [{
      query: { maxWidth: 640 },
      option: {
        grid: { left: 4, right: 8, top: 12, bottom: showZoom ? 80 : 32 },
        xAxis: { axisLabel: { fontSize: 9, rotate: 35 } },
        yAxis: { axisLabel: { fontSize: 9 } },
      },
    }],
  };
}
