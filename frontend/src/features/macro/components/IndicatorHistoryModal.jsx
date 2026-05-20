import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import ReactECharts from 'echarts-for-react';
import { useTheme } from '../../../shared/context/useTheme';
import BaseModal from '../../../shared/components/modal/BaseModal';
import Spinner from '../../../shared/components/feedback/Spinner';
import { useMacroIndicatorHistory } from '../hooks/useMacroIndicators';

const RANGES = [
  { id: '1Y', years: 1, labelKey: 'rangeOneYear' },
  { id: '3Y', years: 3, labelKey: 'rangeThreeYears' },
  { id: '5Y', years: 5, labelKey: 'rangeFiveYears' },
  { id: 'ALL', years: 30, labelKey: 'rangeAll' },
];

function toIso(d) {
  return d.toISOString().slice(0, 10);
}

function rangeBounds(years) {
  const to = new Date();
  const from = new Date(to);
  from.setFullYear(from.getFullYear() - years);
  return { from: toIso(from), to: toIso(to) };
}

export default function IndicatorHistoryModal({ indicator, onClose }) {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const [range, setRange] = useState('1Y');
  const bounds = useMemo(() => {
    const selected = RANGES.find((r) => r.id === range) || RANGES[0];
    return rangeBounds(selected.years);
  }, [range]);

  const { data: points = [], isLoading } = useMacroIndicatorHistory(indicator?.code, bounds);

  const label = t(`marketOverview.macro.${indicator?.label}`, { defaultValue: indicator?.label || '' });

  const option = useMemo(() => buildOption(points, indicator?.unit, isDark), [points, indicator?.unit, isDark]);

  return (
    <BaseModal
      isOpen
      onClose={onClose}
      title={t('marketOverview.macro.historyTitle', { label })}
      size="lg"
    >
      <div className="space-y-3">
        <div className="flex flex-wrap items-center gap-1.5">
          {RANGES.map((r) => (
            <button
              key={r.id}
              type="button"
              onClick={() => setRange(r.id)}
              className={`text-xs font-mono font-semibold rounded-md px-3 py-1.5 transition-colors border-none cursor-pointer ${
                range === r.id
                  ? 'bg-accent/20 text-accent'
                  : 'bg-bg-elevated text-fg-muted hover:bg-bg-base hover:text-fg'
              }`}
            >
              {t(`marketOverview.macro.${r.labelKey}`)}
            </button>
          ))}
        </div>

        <div className="relative h-[280px] sm:h-[360px]">
          {isLoading && (
            <div className="absolute inset-0 flex items-center justify-center">
              <Spinner size="md" tone="accent" />
            </div>
          )}
          {!isLoading && points.length > 0 && (
            <ReactECharts option={option} style={{ height: '100%', width: '100%' }} opts={{ renderer: 'canvas' }} />
          )}
        </div>
      </div>
    </BaseModal>
  );
}

function buildOption(points, unit, isDark) {
  const muted = isDark ? '#6b6b7a' : '#94a3b8';
  const grid = isDark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.05)';
  const accent = isDark ? '#5E6AD2' : '#4338ca';
  const data = points.map((p) => [new Date(p.observedAt).getTime(), Number(p.value)]);

  return {
    backgroundColor: 'transparent',
    animation: data.length < 200,
    grid: { left: 56, right: 16, top: 16, bottom: 32 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'time',
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: muted, fontSize: 10 },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: muted,
        fontSize: 10,
        formatter: (val) => (unit === 'PERCENT' ? `%${val.toFixed(1)}` : val.toLocaleString('tr-TR')),
      },
      splitLine: { lineStyle: { color: grid, type: 'dashed' } },
    },
    series: [{
      type: 'line',
      smooth: true,
      showSymbol: false,
      data,
      itemStyle: { color: accent },
      lineStyle: { width: 2, color: accent },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: `${accent}55` },
            { offset: 1, color: `${accent}00` },
          ],
        },
      },
    }],
  };
}
