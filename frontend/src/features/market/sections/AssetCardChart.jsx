import { useEffect, useMemo, useRef } from 'react';
import ReactECharts from 'echarts-for-react';
import useDeferredVisibility from '../../../shared/hooks/useDeferredVisibility';
import { areaGradient, CHART_ACCENT } from '../../../shared/charts/echartsTheme';

function lineColor(changePercent) {
  const normalized = Number(changePercent);
  if (!Number.isFinite(normalized) || normalized === 0) return CHART_ACCENT;
  return normalized > 0 ? '#10b981' : '#ef4444';
}

function fnvHash(str) {
  let h = 2166136261;
  for (let i = 0; i < str.length; i += 1) {
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

function mulberry32(seedInit) {
  let seed = seedInit | 0;
  return () => {
    seed = (seed + 0x6D2B79F5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function generateSparkline(seed, changePercent) {
  const points = 36;
  const seedHash = fnvHash(seed);
  const rand = mulberry32(seedHash);
  const normalized = Number(changePercent);
  const change = Number.isFinite(normalized) ? normalized : 0;
  const start = 100;
  const end = start * (1 + change / 100);

  if (change === 0) {
    const flat = new Array(points);
    const drift = (rand() - 0.5) * 0.4;
    const vol = 0.35;
    let walk = 0;
    for (let i = 0; i < points; i += 1) {
      walk += (rand() - 0.5) * vol + drift / points;
      flat[i] = Number((start + walk).toFixed(3));
    }
    return flat;
  }

  const absChange = Math.abs(change);
  const stepStd = Math.min(absChange * 0.05, 0.4) + 0.05;

  const walk = new Array(points);
  walk[0] = 0;
  for (let i = 1; i < points; i += 1) {
    const u1 = Math.max(rand(), 1e-9);
    const u2 = rand();
    const gauss = Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
    walk[i] = walk[i - 1] + gauss * stepStd;
  }
  const wEnd = walk[points - 1];

  const out = new Array(points);
  for (let i = 0; i < points; i += 1) {
    const t = i / (points - 1);
    const bridge = walk[i] - t * wEnd;
    out[i] = Number((start + (end - start) * t + bridge).toFixed(3));
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

export default function AssetCardChart({ assetCode, changePercent, delayMs = 0 }) {
  const normalizedChangePercent = useMemo(() => {
    const parsed = Number(changePercent);
    return Number.isFinite(parsed) ? parsed : 0;
  }, [changePercent]);
  const data = useMemo(() => generateSparkline(assetCode, normalizedChangePercent), [assetCode, normalizedChangePercent]);
  const color = useMemo(() => lineColor(normalizedChangePercent), [normalizedChangePercent]);
  const option = useMemo(() => buildOption(data, color), [data, color]);
  const [ref, ready] = useDeferredVisibility(delayMs);
  const instanceRef = useRef(null);

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
      {ready && (
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
