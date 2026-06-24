import { formatPrice, currentLocaleTag } from '../../../shared/utils/formatters';

const LINE_COLOR = '#6366f1';
const UNIT_COLOR = '#f59e0b';

const POSITION_EVENT_TYPES = new Set(['POSITION_ADDED', 'POSITION_SOLD']);

export function formatChartMoney(value, currency) {
  if (value == null || !Number.isFinite(value)) return '—';
  const abs = Math.abs(value);
  if (abs >= 100_000) {
    return new Intl.NumberFormat(currentLocaleTag(), {
      notation: 'compact',
      style: 'currency',
      currency,
      maximumFractionDigits: 2,
    }).format(value);
  }
  return formatPrice(value, { currency, minDecimals: 2 });
}

/** Full-precision money for the chart TOOLTIP — never compact. The axis labels stay compact (limited width),
 *  but the hover tooltip must show the exact figure: a TRY value in the millions read "₺1,2M" (compact, 1
 *  decimal) and looked rounded; here it reads the full "₺1.234.567,89". */
export function formatChartMoneyFull(value, currency) {
  if (value == null || !Number.isFinite(value)) return '—';
  return formatPrice(value, { currency, minDecimals: 2 });
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

export function buildAssetChartOption(data, isDark, t, convertAt, displayCurrency, nativeCurrency) {
  if (!data || data.length === 0) return null;

  const muted = isDark ? '#6b6b7a' : '#94a3b8';
  const grid = isDark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.05)';
  const tooltipBg = isDark ? 'rgba(12,12,20,0.95)' : 'rgba(255,255,255,0.97)';
  const tooltipFg = isDark ? '#e2e2ea' : '#1a1a2e';
  const tooltipBorder = isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.08)';
  const targetCurrency = displayCurrency === 'ORIGINAL' || !displayCurrency ? (nativeCurrency || 'TRY') : displayCurrency;

  const seriesData = data.map((d) => {
    const dateStr = new Date(d.timestamp).toLocaleDateString('sv-SE');
    // Plot DIRECTION-AWARE equity (cost + signed PnL), not the raw notional marketValueTry: a SHORT's notional
    // falls as it profits, so plotting notional renders a short — or a mixed long+short symbol — as if both legs
    // were long. For spot holdings cost+pnl equals marketValue, so the line is unchanged there.
    const costTry = Number(d.totalCostTry);
    const equityTry = Number.isFinite(costTry) ? costTry + (Number(d.pnlTry) || 0) : Number(d.marketValueTry) || 0;
    return {
      value: [new Date(d.timestamp).getTime(), Number(convertAt(equityTry, 'TRY', dateStr, nativeCurrency) ?? 0)],
      unitPrice: Number(convertAt(d.unitPriceTry, 'TRY', dateStr, nativeCurrency) ?? 0),
      quantity: Number(d.quantity ?? 0),
      // Convert each event's TRY value at its own date too, so the tooltip's event row matches the
      // line/axis currency (it is formatted with targetCurrency but was previously left raw TRY).
      events: (d.events || []).map((ev) => ({
        ...ev,
        valueTry: Number(convertAt(ev.valueTry, 'TRY', dateStr, nativeCurrency) ?? ev.valueTry),
      })),
    };
  });

  const values = seriesData.map((d) => d.value[1]);
  const dataMin = Math.min(...values);
  const dataMax = Math.max(...values);
  const span = dataMax - dataMin;
  // A near-flat series (e.g. a USD holding whose native value barely moves) would otherwise auto-scale to a
  // microscopic y-range, turning sub-percent FX round-trip noise into a full-height "barcode". Below a 1%
  // move, pad to a comfortable fraction of the level so a flat line reads flat; real movement keeps the
  // tight span-based padding untouched.
  const level = Math.abs(dataMax) || 1;
  const padding = span > level * 0.01 ? span * 0.08 : level * 0.025;

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
      confine: true,
      position: (point, _params, _dom, _rect, size) => {
        const x = Math.max(8, Math.min(point[0] - size.contentSize[0] / 2, size.viewSize[0] - size.contentSize[0] - 8));
        return [x, 8];
      },
      backgroundColor: tooltipBg,
      borderColor: tooltipBorder,
      textStyle: { color: tooltipFg, fontSize: 11 },
      formatter: (params) => {
        const point = params?.[0]?.data;
        if (!point) return '';
        const date = new Date(point.value[0]).toLocaleDateString(currentLocaleTag(), { day: '2-digit', month: 'short', year: 'numeric' });
        const market = formatChartMoneyFull(point.value[1], targetCurrency);
        const unit = formatChartMoneyFull(point.unitPrice, targetCurrency);
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
                <span style="font-size:10px;font-family:ui-monospace,monospace;color:${tooltipFg};opacity:0.85">${formatChartMoneyFull(Number(ev.valueTry) || 0, targetCurrency)}</span>
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
      // Floor at 0 only when every value is non-negative (a price/value chart shouldn't dip below 0 for a
      // tiny pad). VIOP equity CAN go negative (short/futures), so when dataMin < 0 let the axis show it
      // instead of clipping the line at the baseline.
      min: dataMin < 0 ? dataMin - padding : Math.max(0, dataMin - padding),
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
        // Fill from the line to the axis BOTTOM (not 0): for a negative VIOP series the default origin:0 fills
        // upward from the line to 0 (inverted look); 'start' keeps the normal under-the-line gradient.
        origin: 'start',
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
