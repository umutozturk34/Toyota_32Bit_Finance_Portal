import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import ReactECharts from 'echarts-for-react';
import { useTheme } from '../../../shared/context/useTheme';
import { useRateHistory } from '../../../shared/hooks/useRateHistory';
import { SERIES_COLORS } from '../constants';

export default function CompareChart({ scenario, height = 380 }) {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const { currency: displayCurrency, convertAt } = useRateHistory();
  const targetCurrency = displayCurrency === 'ORIGINAL' ? 'TRY' : displayCurrency;
  const option = useMemo(
    () => buildOption(scenario, isDark, targetCurrency, convertAt),
    [scenario, isDark, targetCurrency, convertAt],
  );

  if (!scenario || !scenario.series?.length) {
    return (
      <div
        className="flex items-center justify-center rounded-xl border border-border-default/60 bg-bg-base/40 text-xs text-fg-muted font-mono"
        style={{ height }}
      >
        {t('analytics.noData', { defaultValue: 'Veri yok' })}
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-border-default/60 bg-bg-base/40 overflow-hidden" style={{ height }}>
      <ReactECharts option={option} style={{ height: '100%', width: '100%' }} opts={{ renderer: 'canvas' }} notMerge />
    </div>
  );
}

function buildOption(scenario, isDark, displayCurrency, convertAt) {
  const muted = isDark ? '#6b6b7a' : '#94a3b8';
  const grid = isDark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.05)';
  const tooltipBg = isDark ? 'rgba(12,12,20,0.96)' : 'rgba(255,255,255,0.98)';
  const tooltipFg = isDark ? '#e2e2ea' : '#1a1a2e';

  const series = (scenario?.series || []).map((s, idx) => {
    const color = SERIES_COLORS[idx % SERIES_COLORS.length];
    const data = (s.points || []).map((p) => {
      const tryValue = Number(p.value);
      const displayValue = convertAt ? convertAt(tryValue, 'TRY', p.date) : tryValue;
      return [new Date(p.date).getTime(), Number(displayValue ?? tryValue)];
    });
    const label = s.instrument?.code || '';
    return {
      name: label,
      type: 'line',
      smooth: data.length < 200,
      showSymbol: false,
      sampling: 'lttb',
      data,
      itemStyle: { color },
      lineStyle: { width: 2, color },
    };
  });

  return {
    backgroundColor: 'transparent',
    animation: true,
    grid: { left: 64, right: 24, top: 36, bottom: 32 },
    legend: {
      type: 'scroll',
      top: 6,
      textStyle: { color: muted, fontSize: 11, fontFamily: 'ui-monospace,monospace' },
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: tooltipBg,
      borderWidth: 0,
      textStyle: { color: tooltipFg, fontSize: 11 },
      axisPointer: { type: 'cross', label: { backgroundColor: muted } },
      formatter: (params) => {
        if (!params?.length) return '';
        const date = new Date(params[0].value[0]).toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: 'numeric' });
        const rows = params
          .sort((a, b) => Number(b.value[1]) - Number(a.value[1]))
          .map((p) => {
            const val = Number(p.value[1]).toLocaleString('tr-TR', { style: 'currency', currency: displayCurrency, maximumFractionDigits: 0 });
            return `<div style="display:flex;justify-content:space-between;gap:14px;align-items:center;font-family:ui-monospace,monospace;font-size:11px">
              <span style="display:flex;align-items:center;gap:6px"><span style="width:8px;height:8px;background:${p.color};border-radius:50%"></span>${p.seriesName}</span>
              <span style="font-weight:700;color:${p.color}">${val}</span>
            </div>`;
          }).join('');
        return `<div style="padding:6px 4px;min-width:200px">
          <div style="font-size:10px;color:${tooltipFg};opacity:0.7;margin-bottom:6px">${date}</div>
          ${rows}
        </div>`;
      },
    },
    xAxis: {
      type: 'time',
      axisLine: { lineStyle: { color: grid } },
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
        formatter: (val) => Number(val).toLocaleString('tr-TR', { maximumFractionDigits: 0 }),
      },
      splitLine: { lineStyle: { color: grid, type: 'dashed' } },
    },
    series,
  };
}
