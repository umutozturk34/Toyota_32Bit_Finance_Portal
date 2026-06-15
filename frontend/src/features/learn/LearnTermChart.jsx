import { useMemo } from 'react';
import ReactECharts from 'echarts-for-react';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../../shared/context/useTheme';

// Tiny illustrative chart for a learn term (NOT live data — a schematic so the reader sees what the indicator
// looks like). Deterministic synthetic series, so it renders identically every time. Currently: RSI (oscillator
// with 30/70 bands) and MACD (two lines + histogram).
const RSI = [52, 58, 64, 71, 76, 73, 66, 58, 49, 41, 33, 28, 31, 38, 46, 55, 63, 60];
const MACD_FAST = [-0.4, -0.2, 0.1, 0.5, 0.9, 1.1, 1.0, 0.7, 0.3, -0.1, -0.5, -0.8, -0.7, -0.3, 0.2, 0.6, 0.9, 0.8];
const MACD_SLOW = [-0.2, -0.2, -0.1, 0.1, 0.4, 0.7, 0.8, 0.7, 0.5, 0.2, -0.1, -0.4, -0.5, -0.4, -0.1, 0.2, 0.5, 0.6];

export default function LearnTermChart({ chart }) {
  const { isDark } = useTheme();
  const { t } = useTranslation();
  const muted = isDark ? '#6b6b7a' : '#94a3b8';
  const grid = isDark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.05)';

  const option = useMemo(() => {
    const base = {
      backgroundColor: 'transparent',
      grid: { left: 6, right: 6, top: 10, bottom: 6, containLabel: true },
      xAxis: { type: 'category', show: false, data: RSI.map((_, i) => i), boundaryGap: chart === 'macd' },
      tooltip: { show: false },
      animation: false,
    };
    if (chart === 'rsi') {
      return {
        ...base,
        yAxis: { type: 'value', min: 0, max: 100, splitLine: { lineStyle: { color: grid } }, axisLabel: { color: muted, fontSize: 9 } },
        series: [
          { type: 'line', data: RSI, smooth: true, symbol: 'none', lineStyle: { color: '#8b5cf6', width: 2 } },
          { type: 'line', data: RSI.map(() => 70), symbol: 'none', lineStyle: { color: '#f87171', width: 1, type: 'dashed' } },
          { type: 'line', data: RSI.map(() => 30), symbol: 'none', lineStyle: { color: '#34d399', width: 1, type: 'dashed' } },
        ],
      };
    }
    // macd
    return {
      ...base,
      yAxis: { type: 'value', splitLine: { lineStyle: { color: grid } }, axisLabel: { color: muted, fontSize: 9 } },
      series: [
        {
          type: 'bar',
          data: MACD_FAST.map((v, i) => v - MACD_SLOW[i]),
          itemStyle: { color: (p) => (p.value >= 0 ? '#34d399' : '#f87171') },
          barWidth: '55%',
        },
        { type: 'line', data: MACD_FAST, symbol: 'none', smooth: true, lineStyle: { color: '#6366f1', width: 2 } },
        { type: 'line', data: MACD_SLOW, symbol: 'none', smooth: true, lineStyle: { color: '#f59e0b', width: 2 } },
      ],
    };
  }, [chart, grid, muted]);

  return (
    <div className="mt-2 rounded-lg border border-border-default bg-bg-base/40 p-1.5">
      <ReactECharts key={isDark} option={option} style={{ height: 96, width: '100%' }} opts={{ renderer: 'canvas' }} notMerge />
      <p className="px-1 pt-0.5 text-[9px] text-fg-subtle">{t('learn.chartSchematic')}</p>
    </div>
  );
}
