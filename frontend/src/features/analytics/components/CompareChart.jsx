import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import ReactECharts from 'echarts-for-react';
import { useTheme } from '../../../shared/context/useTheme';
import {
  chartPalette,
  timeAxis,
  valueAxis,
  dataZoomBlock,
  tooltipBase,
  lineSeriesDefaults,
  legendBase,
} from '../../../shared/charts/echartsTheme';
import { SERIES_COLORS } from '../constants';

export default function CompareChart({ scenario }) {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const scenarioCurrency = scenario?.targetCurrency || 'TRY';
  const option = useMemo(
    () => buildOption(scenario, isDark, scenarioCurrency),
    [scenario, isDark, scenarioCurrency],
  );

  if (!scenario || !scenario.series?.length) {
    return (
      <div className="flex items-center justify-center rounded-xl border border-border-default/60 bg-bg-base/40 text-xs text-fg-muted font-mono h-[280px] sm:h-[380px] lg:h-[440px]">
        {t('analytics.noData', { defaultValue: 'Veri yok' })}
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-border-default/60 bg-bg-base/40 overflow-hidden h-[280px] sm:h-[380px] lg:h-[440px] w-full">
      <ReactECharts option={option} style={{ height: '100%', width: '100%' }} opts={{ renderer: 'canvas' }} notMerge />
    </div>
  );
}

function buildOption(scenario, isDark, displayCurrency) {
  const palette = chartPalette(isDark);

  const series = (scenario?.series || []).map((s, idx) => {
    const color = SERIES_COLORS[idx % SERIES_COLORS.length];
    const data = (s.points || []).map((p) => [new Date(p.date).getTime(), Number(p.value)]);
    return {
      ...lineSeriesDefaults(color, data.length),
      name: s.instrument?.code || '',
      data,
    };
  });

  const totalPoints = series.reduce((acc, s) => acc + (s.data?.length || 0), 0);
  const showZoom = totalPoints >= 30;

  return {
    backgroundColor: 'transparent',
    animation: true,
    grid: { left: 8, right: 12, top: 36, bottom: showZoom ? 64 : 32, containLabel: true },
    dataZoom: showZoom ? dataZoomBlock(palette) : undefined,
    legend: legendBase(palette),
    tooltip: tooltipBase(palette, {
      confine: true,
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
          <div style="font-size:10px;color:${palette.tooltipFg};opacity:0.7;margin-bottom:6px">${date}</div>
          ${rows}
        </div>`;
      },
    }),
    xAxis: timeAxis(palette, { axisLine: { lineStyle: { color: palette.grid } } }),
    yAxis: valueAxis(palette, {
      axisLabel: {
        color: palette.muted, fontSize: 10,
        formatter: (val) => Number(val).toLocaleString('tr-TR', { maximumFractionDigits: 0 }),
      },
    }),
    series,
    media: [{
      query: { maxWidth: 640 },
      option: {
        grid: { left: 4, right: 8, top: 28, bottom: showZoom ? 56 : 24 },
        legend: { top: 'bottom', left: 'center', orient: 'horizontal', textStyle: { fontSize: 9 } },
        xAxis: { axisLabel: { fontSize: 9, rotate: 30 } },
        yAxis: { axisLabel: { fontSize: 9 } },
      },
    }],
  };
}
