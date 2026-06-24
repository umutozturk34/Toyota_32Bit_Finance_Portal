import { useMemo, useState } from 'react';
import ReactECharts from 'echarts-for-react';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../../shared/context/useTheme';
import { useMacroIndicatorHistory } from '../macro/hooks/useMacroIndicators';
import { SkeletonChart } from '../../shared/components/feedback/Skeleton';

// Plots the app's OWN live macro series (real EVDS data) for a learn card. Takes the indicator CODE directly and
// reads each point's `observedAt` date (the macro history shape) on a time axis, so labels format themselves
// instead of rendering "undefined". A schematic-free, real curve of inflation / rates / deposits.
export default function LearnMacroChart({ code, color = '#6366f1' }) {
  const { isDark } = useTheme();
  const { t } = useTranslation();
  // Lazy-init the window so render stays pure (no new Date() during render).
  const [range] = useState(() => {
    const now = new Date();
    return { from: `${now.getFullYear() - 5}-01-01`, to: now.toISOString().slice(0, 10) };
  });
  const { data: points = [], isLoading } = useMacroIndicatorHistory(code, range);

  const option = useMemo(() => {
    const muted = isDark ? '#6b6b7a' : '#94a3b8';
    const grid = isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.05)';
    const data = (Array.isArray(points) ? points : [])
      .map((p) => {
        const ts = new Date(p?.observedAt ?? p?.date).getTime();
        return Number.isFinite(ts) && p?.value != null ? [ts, Number(p.value)] : null;
      })
      .filter(Boolean);
    return {
      backgroundColor: 'transparent',
      grid: { left: 6, right: 10, top: 10, bottom: 6, containLabel: true },
      tooltip: {
        trigger: 'axis', confine: true, borderWidth: 0,
        backgroundColor: isDark ? 'rgba(12,12,20,0.96)' : 'rgba(255,255,255,0.98)',
        textStyle: { color: isDark ? '#e2e2ea' : '#1a1a2e', fontSize: 11 },
      },
      xAxis: {
        type: 'time',
        axisLine: { lineStyle: { color: grid } }, axisTick: { show: false },
        axisLabel: { color: muted, fontSize: 9, hideOverlap: true },
      },
      yAxis: {
        type: 'value', scale: true, splitLine: { lineStyle: { color: grid } },
        axisLabel: { color: muted, fontSize: 9 },
      },
      series: [{
        type: 'line', data, symbol: 'none', smooth: true, lineStyle: { color, width: 2 },
        areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: `${color}33` }, { offset: 1, color: `${color}00` }] } },
      }],
      animation: false,
    };
  }, [points, isDark, color]);

  return (
    <div className="mt-3 rounded-xl border border-border-default bg-bg-base/40 p-2">
      {isLoading ? (
        <SkeletonChart h="140px" />
      ) : (
        <ReactECharts key={isDark} option={option} style={{ height: 140, width: '100%' }} opts={{ renderer: 'canvas' }} notMerge />
      )}
      <p className="px-1 pt-0.5 text-[9px] text-fg-subtle">{t('learn.chartLive')}</p>
    </div>
  );
}
