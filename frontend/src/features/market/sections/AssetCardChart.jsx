import { lazy, Suspense, useMemo } from 'react';

const ReactECharts = lazy(() => import('echarts-for-react'));

function lineColor(changePercent) {
  if (!Number.isFinite(changePercent) || changePercent === 0) return '#6366f1';
  return changePercent > 0 ? '#10b981' : '#ef4444';
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
  const points = 28;
  const seedHash = fnvHash(seed);
  const rand = mulberry32(seedHash);
  const change = Number.isFinite(changePercent) ? changePercent : 0;
  const start = 100;

  if (change === 0) {
    return new Array(points).fill(start);
  }

  const end = start * (1 + change / 100);
  const totalDelta = end - start;
  const absChange = Math.abs(change);

  const variant = seedHash % 4;
  const noiseAmplitude = absChange * 0.18;

  const out = new Array(points);
  for (let i = 0; i < points; i += 1) {
    const t = i / (points - 1);
    let eased;
    switch (variant) {
      case 0:
        eased = t * t * (3 - 2 * t);
        break;
      case 1:
        eased = Math.pow(t, 1.6);
        break;
      case 2:
        eased = 1 - Math.pow(1 - t, 1.6);
        break;
      default:
        eased = 0.5 * (1 - Math.cos(Math.PI * t));
    }
    const baseValue = start + totalDelta * eased;
    const noise = (rand() - 0.5) * noiseAmplitude * 2;
    out[i] = Number((baseValue + noise).toFixed(3));
  }
  return out;
}

function buildOption(data, color, delayMs) {
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
    animationDelay: delayMs,
    animationEasing: 'cubicOut',
    series: [{
      type: 'line',
      data,
      smooth: 0.3,
      symbol: 'none',
      lineStyle: { color, width: 1.4 },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: `${color}26` },
            { offset: 1, color: `${color}00` },
          ],
        },
      },
      animationDuration: 1100,
      animationDelay: delayMs,
      animationEasing: 'cubicOut',
    }],
  };
}

/** @param {{assetCode: string, changePercent: number, delayMs?: number}} props */
export default function AssetCardChart({ assetCode, changePercent, delayMs = 0 }) {
  const data = useMemo(() => generateSparkline(assetCode, changePercent), [assetCode, changePercent]);
  const color = useMemo(() => lineColor(changePercent), [changePercent]);
  const option = useMemo(() => buildOption(data, color, delayMs), [data, color, delayMs]);
  return (
    <div
      className="absolute inset-0 pointer-events-none opacity-[0.42]"
      aria-hidden="true"
    >
      <Suspense fallback={null}>
        <ReactECharts option={option} style={{ width: '100%', height: '100%' }} opts={{ renderer: 'svg' }} />
      </Suspense>
    </div>
  );
}
