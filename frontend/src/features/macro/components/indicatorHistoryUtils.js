import { isMacro } from '../../analytics/lib/compareSeriesUtils';
import { themeFor } from '../utils';

const MACRO_CATEGORY_TO_TYPE = {
  DEPOSIT: 'MACRO_DEPOSIT',
  INFLATION: 'MACRO_INFLATION',
  RATES: 'MACRO_RATE',
};

export function normalizeSelected(raw, fallbackType) {
  let type = raw.type || raw.assetType || fallbackType;
  if (!type && raw.category) type = MACRO_CATEGORY_TO_TYPE[raw.category];
  if (!type) type = 'STOCK';
  return {
    type,
    code: raw.code,
    name: raw.name || raw.label || raw.code,
    label: raw.label,
    unit: raw.unit,
    frequency: raw.frequency,
    category: raw.category,
    currency: raw.currency,
    maturity: raw.maturity,
    lastValue: raw.lastValue,
    lastDate: raw.lastDate,
  };
}

export function colorFor(item) {
  if (isMacro(item.type) && item.category) {
    return themeFor(item.category).accent;
  }
  return '#5E6AD2';
}

export function buildOption(seriesData, normalize, isDark, localeTag = localeTag) {
  const muted = isDark ? '#6b6b7a' : '#94a3b8';
  const grid = isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)';
  const tooltipBg = isDark ? 'rgba(12,12,20,0.96)' : 'rgba(255,255,255,0.98)';
  const tooltipFg = isDark ? '#e2e2ea' : '#1a1a2e';
  const single = seriesData.length === 1;

  const series = seriesData.map(({ indicator: ind, points, color }) => {
    if (!points || points.length === 0) return null;
    const sortedPoints = [...points].sort((a, b) =>
      String(a.date).localeCompare(String(b.date)));
    const basePoint = Number(sortedPoints[0]?.value);
    const data = sortedPoints.map((p) => {
      const raw = Number(p.value);
      const plotted = normalize && basePoint !== 0
        ? ((raw - basePoint) / Math.abs(basePoint)) * 100
        : raw;
      const pct = basePoint !== 0 ? ((raw - basePoint) / Math.abs(basePoint)) * 100 : 0;
      return [new Date(p.date).getTime(), plotted, raw, pct];
    });
    return {
      name: ind.code,
      type: 'line',
      smooth: data.length < 200,
      showSymbol: false,
      sampling: 'lttb',
      data,
      itemStyle: { color },
      lineStyle: { width: 2, color },
      areaStyle: single ? {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: `${color}55` },
            { offset: 1, color: `${color}00` },
          ],
        },
      } : null,
      _unit: ind.unit,
    };
  }).filter(Boolean);

  const totalPoints = series.reduce((acc, s) => acc + (s.data?.length || 0), 0);
  const showZoom = totalPoints >= 2;
  series.forEach((s, idx) => {
    s.animationDuration = 1100;
    s.animationEasing = 'cubicOut';
    s.animationDelay = idx * 180;
  });

  return {
    backgroundColor: 'transparent',
    animation: true,
    animationThreshold: 100000,
    grid: { left: 56, right: 16, top: single ? 16 : 32, bottom: showZoom ? 64 : 32, containLabel: false },
    legend: !single ? {
      type: 'scroll',
      top: 4,
      textStyle: { color: muted, fontSize: 10, fontFamily: 'ui-monospace,monospace' },
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
    } : undefined,
    dataZoom: showZoom ? [
      {
        type: 'inside',
        filterMode: 'filter',
        zoomOnMouseWheel: true,
        moveOnMouseMove: false,
        moveOnMouseWheel: false,
      },
      {
        type: 'slider',
        height: 18,
        bottom: 8,
        filterMode: 'filter',
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
        textStyle: { color: muted, fontSize: 9 },
      },
    ] : undefined,
    tooltip: {
      trigger: 'axis',
      confine: true,
      position: (point, _params, _dom, _rect, size) => {
        const x = Math.max(8, Math.min(point[0] - size.contentSize[0] / 2, size.viewSize[0] - size.contentSize[0] - 8));
        return [x, 8];
      },
      backgroundColor: tooltipBg,
      borderWidth: 0,
      textStyle: { color: tooltipFg, fontSize: 11 },
      formatter: (params) => {
        if (!params?.length) return '';
        const date = new Date(params[0].value[0]).toLocaleDateString(localeTag, { day: '2-digit', month: 'short', year: 'numeric' });
        const rows = params.map((p) => {
          const seriesDef = series.find((s) => s.name === p.seriesName);
          const unit = seriesDef?._unit;
          const raw = Number(p.value[2]);
          const pct = Number(p.value[3]);
          const rawFmt = unit === 'PERCENT'
            ? `%${raw.toFixed(2)}`
            : raw.toLocaleString(localeTag, { maximumFractionDigits: 2 });
          const sign = pct > 0 ? '+' : '';
          const pctColor = pct > 0 ? '#10b981' : pct < 0 ? '#ef4444' : tooltipFg;
          const pctFmt = `${sign}${pct.toFixed(2)}%`;
          return `<div style="display:flex;justify-content:space-between;gap:14px;align-items:center;padding:3px 0;font-family:ui-monospace,monospace;font-size:11px">
            <span style="display:flex;align-items:center;gap:6px;min-width:0">
              <span style="width:6px;height:6px;border-radius:50%;background:${p.color};flex-shrink:0"></span>
              <span style="color:${tooltipFg};opacity:0.85">${p.seriesName}</span>
            </span>
            <span style="display:flex;align-items:baseline;gap:8px;flex-shrink:0">
              <span style="font-weight:700;color:${p.color}">${rawFmt}</span>
              <span style="font-size:10px;font-weight:600;color:${pctColor};opacity:0.9">${pctFmt}</span>
            </span>
          </div>`;
        }).join('');
        return `<div style="padding:6px 4px;min-width:220px">
          <div style="font-size:10px;color:${tooltipFg};opacity:0.65;margin-bottom:6px">${date}</div>
          ${rows}
        </div>`;
      },
    },
    xAxis: {
      type: 'time',
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: muted, fontSize: 10 },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: muted, fontSize: 10,
        formatter: (val) => {
          if (normalize) {
            const sign = val > 0 ? '+' : '';
            return `${sign}${val.toFixed(0)}%`;
          }
          const unit = series[0]?._unit;
          return unit === 'PERCENT' ? `%${val.toFixed(1)}` : val.toLocaleString(localeTag);
        },
      },
      splitLine: { lineStyle: { color: grid, type: 'dashed' } },
    },
    series,
    media: [{
      query: { maxWidth: 640 },
      option: {
        grid: { left: 32, right: 8, top: single ? 12 : 24, bottom: showZoom ? 56 : 24 },
        legend: !single ? { top: 'bottom', left: 'center', orient: 'horizontal', textStyle: { fontSize: 9 } } : undefined,
        xAxis: { axisLabel: { fontSize: 9, rotate: 30 } },
        yAxis: { axisLabel: { fontSize: 9 } },
      },
    }],
  };
}
