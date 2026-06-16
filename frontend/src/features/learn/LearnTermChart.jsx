import { useMemo } from 'react';
import ReactECharts from 'echarts-for-react';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../../shared/context/useTheme';

// Tiny illustrative chart for a learn term (NOT live data — a schematic so the reader sees what the indicator
// looks like). Deterministic synthetic series, so it renders identically every time. Currently: RSI (oscillator
// with 30/70 bands) and MACD (two lines + histogram).
// [open, close, low, high] per bar — a deterministic up-then-down stretch for the candlestick schematic.
const CANDLES = [
  [20, 24, 19, 25], [24, 23, 22, 26], [23, 27, 22, 28], [27, 30, 26, 31], [30, 29, 28, 32],
  [29, 33, 28, 34], [33, 32, 31, 35], [32, 30, 29, 33], [30, 27, 26, 31], [27, 28, 25, 29],
  [28, 25, 24, 29], [25, 22, 21, 26], [22, 24, 21, 25], [24, 21, 20, 25], [21, 23, 20, 24],
];
const RSI = [52, 58, 64, 71, 76, 73, 66, 58, 49, 41, 33, 28, 31, 38, 46, 55, 63, 60];
const MACD_FAST = [-0.4, -0.2, 0.1, 0.5, 0.9, 1.1, 1.0, 0.7, 0.3, -0.1, -0.5, -0.8, -0.7, -0.3, 0.2, 0.6, 0.9, 0.8];
const MACD_SLOW = [-0.2, -0.2, -0.1, 0.1, 0.4, 0.7, 0.8, 0.7, 0.5, 0.2, -0.1, -0.4, -0.5, -0.4, -0.1, 0.2, 0.5, 0.6];

// Option payoff at expiry: underlying price 80→120, strike 100, premium 4. Call = max(0, price−strike) − premium;
// Put = max(0, strike−price) − premium. The classic hockey-stick: loss capped at the premium, breakeven offset by it.
const PAYOFF_X = [80, 84, 88, 92, 96, 100, 104, 108, 112, 116, 120];
const STRIKE = 100;
const PREMIUM = 4;
const CALL_PAYOFF = PAYOFF_X.map((p) => Math.max(0, p - STRIKE) - PREMIUM);
const PUT_PAYOFF = PAYOFF_X.map((p) => Math.max(0, STRIKE - p) - PREMIUM);
// Clean vs dirty price over two coupon periods: the dirty (quoted) price ramps up as the coupon accrues then
// drops on the ex-coupon date (sawtooth); the clean price stays flat. dirty − clean = accrued coupon.
const CLEAN = [100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100];
const DIRTY = [100, 100.9, 101.8, 102.7, 103.6, 100, 100.9, 101.8, 102.7, 103.6, 100, 100.9];

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
    if (chart === 'candles') {
      return {
        ...base,
        xAxis: { type: 'category', show: false, data: CANDLES.map((_, i) => i) },
        yAxis: { type: 'value', scale: true, splitLine: { lineStyle: { color: grid } }, axisLabel: { color: muted, fontSize: 9 } },
        series: [{
          type: 'candlestick', data: CANDLES,
          itemStyle: { color: '#34d399', color0: '#f87171', borderColor: '#34d399', borderColor0: '#f87171' },
        }],
      };
    }
    if (chart === 'callPayoff' || chart === 'putPayoff') {
      const data = chart === 'callPayoff' ? CALL_PAYOFF : PUT_PAYOFF;
      const strikeIdx = PAYOFF_X.indexOf(STRIKE);
      return {
        ...base,
        // Extra top room so the strike markLine label is not clipped at the chart edge.
        grid: { left: 6, right: 6, top: 22, bottom: 6, containLabel: true },
        xAxis: { type: 'category', show: false, data: PAYOFF_X },
        yAxis: { type: 'value', splitLine: { lineStyle: { color: grid } }, axisLabel: { color: muted, fontSize: 9 } },
        series: [
          { type: 'line', data: data.map(() => 0), symbol: 'none', lineStyle: { color: muted, width: 1, type: 'dashed' } },
          {
            type: 'line', data, smooth: false, symbol: 'none', lineStyle: { color: '#6366f1', width: 2 },
            areaStyle: { color: 'rgba(99,102,241,0.12)' },
            markLine: {
              silent: true, symbol: 'none',
              lineStyle: { color: '#f59e0b', width: 1, type: 'dotted' },
              // A solid amber chip with dark text reads clearly over the gridlines and never clips at this size.
              label: {
                show: true, formatter: t('learn.strikeLabel'), position: 'insideEndTop', distance: 3,
                color: '#1a1205', backgroundColor: '#f59e0b', padding: [2, 5], borderRadius: 4,
                fontSize: 9, fontWeight: 'bold',
              },
              data: [{ xAxis: strikeIdx }],
            },
          },
        ],
      };
    }
    if (chart === 'cleanDirty') {
      return {
        ...base,
        xAxis: { type: 'category', show: false, data: DIRTY.map((_, i) => i) },
        yAxis: { type: 'value', scale: true, splitLine: { lineStyle: { color: grid } }, axisLabel: { color: muted, fontSize: 9 } },
        series: [
          { type: 'line', data: DIRTY, smooth: false, symbol: 'none', lineStyle: { color: '#8b5cf6', width: 2 }, name: t('learn.dirtyLabel') },
          { type: 'line', data: CLEAN, smooth: false, symbol: 'none', lineStyle: { color: '#34d399', width: 2, type: 'dashed' }, name: t('learn.cleanLabel') },
        ],
      };
    }
    if (chart === 'valorTimeline') {
      // Settlement (valör) timeline: trade on day T, cash lands T+2. A 3-stop line, the final stop highlighted.
      return {
        ...base,
        grid: { left: 8, right: 8, top: 26, bottom: 22, containLabel: true },
        xAxis: {
          type: 'category', data: ['T', 'T+1', 'T+2'], boundaryGap: true,
          axisLabel: { color: muted, fontSize: 10, fontWeight: 'bold' },
          axisLine: { lineStyle: { color: grid } }, axisTick: { show: false },
        },
        yAxis: { type: 'value', show: false, min: 0, max: 2 },
        series: [{
          type: 'line', data: [1, 1, 1], symbol: 'circle', symbolSize: 11,
          lineStyle: { color: '#2dd4bf', width: 2 },
          itemStyle: { color: (p) => (p.dataIndex === 2 ? '#2dd4bf' : '#5b6472') },
          label: {
            show: true, position: 'top', fontSize: 9, fontWeight: 'bold', color: muted,
            formatter: (p) => (p.dataIndex === 0 ? t('learn.valorTradeDay')
              : p.dataIndex === 2 ? t('learn.valorSettled') : ''),
          },
        }],
      };
    }
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
  }, [chart, grid, muted, t]);

  return (
    <div className="mt-2 rounded-lg border border-border-default bg-bg-base/40 p-1.5">
      <ReactECharts key={isDark} option={option} style={{ height: 96, width: '100%' }} opts={{ renderer: 'canvas' }} notMerge />
      <p className="px-1 pt-0.5 text-[9px] text-fg-subtle">{t('learn.chartSchematic')}</p>
    </div>
  );
}
