import { useEffect, useMemo, useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import ReactECharts from 'echarts-for-react';
import useDeferredVisibility from '../../../shared/hooks/useDeferredVisibility';
import { areaGradient, CHART_ACCENT } from '../../../shared/charts/echartsTheme';
import { unifiedMarketService } from '../../../shared/services/unifiedMarketService';

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

function toSparkSeries(history, maxPoints = 60) {
  if (!Array.isArray(history) || history.length === 0) return [];
  const sorted = [...history].sort((a, b) => {
    const da = a?.candleDate || a?.date || a?.observedAt || '';
    const db = b?.candleDate || b?.date || b?.observedAt || '';
    return String(da).localeCompare(String(db));
  });
  const values = sorted.map(pickClose).filter((v) => Number.isFinite(v));
  if (values.length === 0) return [];
  if (values.length <= maxPoints) return values;
  const step = values.length / maxPoints;
  const out = [];
  for (let i = 0; i < maxPoints; i += 1) {
    out.push(values[Math.floor(i * step)]);
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

  const { data: history } = useQuery({
    queryKey: ['assetCardChart', assetType, assetCode, '1Y'],
    queryFn: () => unifiedMarketService.getHistory(assetType, assetCode, '1Y'),
    enabled: ready && Boolean(assetType) && Boolean(assetCode),
    staleTime: 10 * 60_000,
    gcTime: 30 * 60_000,
    retry: 1,
  });

  const data = useMemo(() => toSparkSeries(history), [history]);
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
