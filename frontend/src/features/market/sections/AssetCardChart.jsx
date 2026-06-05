import { useEffect, useMemo, useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import ReactECharts from 'echarts-for-react';
import useDeferredVisibility from '../../../shared/hooks/useDeferredVisibility';
import { areaGradient, CHART_ACCENT } from '../../../shared/charts/echartsTheme';
import { unifiedMarketService } from '../../../shared/services/unifiedMarketService';
import { useRateHistory } from '../../../shared/hooks/useRateHistory';
import { nativeCurrencyFor } from '../../analytics/lib/compareSeriesUtils';

function lineColor(changePercent) {
  const normalized = Number(changePercent);
  if (!Number.isFinite(normalized) || normalized === 0) return CHART_ACCENT;
  return normalized > 0 ? '#10b981' : '#ef4444';
}

function pickClose(point) {
  const candidates = [point?.close, point?.price, point?.value, point?.sellingPrice, point?.rate];
  for (const c of candidates) {
    const n = Number(c);
    if (Number.isFinite(n) && n !== 0) return n;
  }
  return null;
}

function pickDate(point) {
  const raw = point?.candleDate || point?.date || point?.observedAt || '';
  return String(raw).slice(0, 10);
}

// Returns sorted { date, close } points (close in the asset's native currency) so each can be
// FX-converted at its OWN date by the caller; the date is the local candle day, not a UTC stamp.
function toSparkSeries(history, maxPoints = 60) {
  if (!Array.isArray(history) || history.length === 0) return [];
  const sorted = [...history].sort((a, b) => pickDate(a).localeCompare(pickDate(b)));
  const points = sorted
    .map((p) => ({ date: pickDate(p), close: pickClose(p) }))
    .filter((p) => Number.isFinite(p.close));
  if (points.length === 0) return [];
  if (points.length <= maxPoints) return points;
  const step = points.length / maxPoints;
  const out = [];
  for (let i = 0; i < maxPoints; i += 1) {
    out.push(points[Math.floor(i * step)]);
  }
  return out;
}

function buildOption(data, color) {
  return {
    grid: { top: 8, right: 0, bottom: 0, left: 0, containLabel: false },
    xAxis: { type: 'category', show: false, boundaryGap: false, data: data.map((_, i) => i) },
    yAxis: {
      type: 'value',
      show: false,
      min: (extent) => {
        const span = extent.max - extent.min;
        if (span < 0.001) return extent.min - 0.6;
        return extent.min - span * 0.25;
      },
      max: (extent) => {
        const span = extent.max - extent.min;
        if (span < 0.001) return extent.max + 0.6;
        return extent.max + span * 0.25;
      },
    },
    tooltip: { show: false },
    animation: true,
    animationDuration: 1100,
    animationDelay: 0,
    animationEasing: 'cubicOut',
    series: [{
      type: 'line',
      data,
      smooth: 0.3,
      symbol: 'none',
      lineStyle: { color, width: 1.4 },
      areaStyle: { color: areaGradient(color, 0.15) },
      animationDuration: 1100,
      animationDelay: 0,
      animationEasing: 'cubicOut',
    }],
  };
}

export default function AssetCardChart({ assetType, assetCode, changePercent, delayMs = 0 }) {
  const normalizedChangePercent = useMemo(() => {
    const parsed = Number(changePercent);
    return Number.isFinite(parsed) ? parsed : 0;
  }, [changePercent]);
  const color = useMemo(() => lineColor(normalizedChangePercent), [normalizedChangePercent]);
  const [ref, ready] = useDeferredVisibility(delayMs);
  const instanceRef = useRef(null);
  const { convertAt } = useRateHistory();

  const { data: history } = useQuery({
    queryKey: ['assetCardChart', assetType, assetCode, '1Y'],
    queryFn: () => unifiedMarketService.getHistory(assetType, assetCode, '1Y'),
    enabled: ready && Boolean(assetType) && Boolean(assetCode),
    staleTime: 10 * 60_000,
    gcTime: 30 * 60_000,
    retry: 1,
  });

  // The history closes are in the asset's native currency. Convert every point at its OWN candle date
  // so the sparkline shape tracks the same currency the card's price/change shows — a no-op for
  // TRY-native assets in TRY/ORIGINAL, but per-date FX for USD-native crypto, FX-quoted VIOP, and any
  // asset under a USD/EUR display. natural = native makes ORIGINAL resolve back to the native currency.
  const native = useMemo(() => nativeCurrencyFor(assetType, assetCode), [assetType, assetCode]);
  const data = useMemo(() => {
    const points = toSparkSeries(history);
    return points
      .map((p) => Number(convertAt(p.close, native, p.date, native) ?? p.close))
      .filter((v) => Number.isFinite(v));
  }, [history, convertAt, native]);
  const option = useMemo(() => (data.length > 0 ? buildOption(data, color) : null), [data, color]);

  useEffect(() => {
    if (!ready || !ref.current || typeof ResizeObserver === 'undefined') return undefined;
    const node = ref.current;
    let observer = null;
    let rafId = null;
    let lastWidth = 0;
    const setupTimer = setTimeout(() => {
      observer = new ResizeObserver((entries) => {
        const width = entries[0]?.contentRect?.width ?? 0;
        if (Math.abs(width - lastWidth) < 2) return;
        lastWidth = width;
        if (rafId) cancelAnimationFrame(rafId);
        rafId = requestAnimationFrame(() => {
          const instance = instanceRef.current;
          if (instance && !instance.isDisposed?.()) instance.resize();
        });
      });
      observer.observe(node);
      lastWidth = node.getBoundingClientRect().width;
    }, 1200);
    return () => {
      clearTimeout(setupTimer);
      if (observer) observer.disconnect();
      if (rafId) cancelAnimationFrame(rafId);
    };
  }, [ready, ref]);

  return (
    <div
      ref={ref}
      className="absolute inset-0 pointer-events-none opacity-[0.42]"
      aria-hidden="true"
    >
      {ready && option && (
        <ReactECharts
          option={option}
          style={{ width: '100%', height: '100%' }}
          opts={{ renderer: 'svg' }}
          onChartReady={(instance) => { instanceRef.current = instance; }}
        />
      )}
    </div>
  );
}
