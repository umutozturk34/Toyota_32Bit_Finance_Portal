import { formatPrice, currentLocaleTag } from '../../../shared/utils/formatters';

const LINE_COLOR = '#6366f1';
const UNIT_COLOR = '#f59e0b';

const POSITION_EVENT_TYPES = new Set(['POSITION_ADDED', 'POSITION_SOLD']);

export function formatChartMoney(value, currency) {
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

export function buildAssetChartOption(data, isDark, t, convertAt, displayCurrency) {
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
        const events = (point.events || []).filter((e) => POSITION_EVENT_TYPES.has(e.type));
        const eventsBlock = events.length === 0 ? '' : `
          <div style="border-top:1px solid ${tooltipBorder};margin-top:6px;padding-top:6px">
            <div style="font-size:9px;text-transform:uppercase;letter-spacing:0.8px;color:${tooltipFg};opacity:0.55;margin-bottom:4px">${t('portfolio.performance.positionEvents')}</div>
            ${events.map((ev) => {
              const isSold = ev.type === 'POSITION_SOLD';
              const color = isSold ? '#ef4444' : '#10b981';
              const label = t(isSold ? 'portfolio.performance.lotSold' : 'portfolio.performance.lotAdded');
              const codeLabel = ev.assetCode || ev.assetType || '';
              const qty = ev.quantity != null ? Number(ev.quantity) : null;
              const qtyText = qty != null && qty > 0
                ? `<span style="font-size:10px;font-family:ui-monospace,monospace;color:${tooltipFg};opacity:0.55">×${qty.toLocaleString(currentLocaleTag(), { maximumFractionDigits: 8 })}</span>`
                : '';
              return `<div style="display:flex;align-items:center;justify-content:space-between;gap:10px;padding:3px 0">
                <div style="display:flex;align-items:center;gap:5px">
                  <span style="width:5px;height:5px;border-radius:50%;background:${color};display:inline-block"></span>
                  <span style="font-size:10px;font-weight:600;color:${color}">${label}</span>
                  <span style="font-size:10px;color:${tooltipFg};opacity:0.6">${codeLabel}</span>
                  ${qtyText}
                </div>
                <span style="font-size:10px;font-family:ui-monospace,monospace;color:${tooltipFg};opacity:0.85">${formatChartMoney(Number(ev.valueTry) || 0, targetCurrency)}</span>
              </div>`;
            }).join('')}
          </div>`;
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
            ${eventsBlock}
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
