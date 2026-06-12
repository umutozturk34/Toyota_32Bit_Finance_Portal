import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import ReactECharts from 'echarts-for-react';
import { useTheme } from '../../../shared/context/useTheme';
import useMediaQuery from '../../../shared/hooks/useMediaQuery';
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
import { moneyDigits } from '../utils';

// 0 decimals at normal magnitude (>=1) keeps the axis/tooltip byte-identical to before; only a sub-1
// value borrows moneyDigits' extra precision so a real small price never renders as a flat "0".
const chartDigits = (val) => (Math.abs(val) >= 1 ? 0 : moneyDigits(val));

// Last data point at or before `ts` (data sorted ascending by ts); lets the tooltip show every series'
// value-in-force at the hovered date even when series end on different dates (e.g. a deposit's last EVDS
// observation vs a daily commodity), so no series silently drops out of the tooltip mid-hover.
function valueAsOf(data, ts) {
  if (!data || data.length === 0) return null;
  let lo = 0;
  let hi = data.length - 1;
  let ans = -1;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    if (data[mid][0] <= ts) { ans = mid; lo = mid + 1; } else { hi = mid - 1; }
  }
  return ans >= 0 ? data[ans] : null;
}

export default function CompareChart({ scenario }) {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const isMobile = useMediaQuery('(max-width: 640px)');
  const scenarioCurrency = scenario?.targetCurrency || 'TRY';
  const option = useMemo(
    () => buildOption(scenario, isDark, scenarioCurrency, isMobile),
    [scenario, isDark, scenarioCurrency, isMobile],
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

function buildOption(scenario, isDark, displayCurrency, isMobile) {
  const palette = chartPalette(isDark);

  const series = (scenario?.series || []).map((s, idx) => {
    const color = SERIES_COLORS[idx % SERIES_COLORS.length];
    const data = (s.points || []).map((p) => [new Date(p.date).getTime(), Number(p.value)]);
    return {
      ...lineSeriesDefaults(color, data.length),
      name: s.instrument?.code || '',
      data,
      _color: color,
    };
  });

  const totalPoints = series.reduce((acc, s) => acc + (s.data?.length || 0), 0);
  const showZoom = totalPoints >= 30;

  return {
    backgroundColor: 'transparent',
    animation: true,
    grid: {
      left: isMobile ? 4 : 8, right: isMobile ? 8 : 12, top: isMobile ? 28 : 36,
      bottom: showZoom ? (isMobile ? 56 : 64) : (isMobile ? 24 : 32), containLabel: true,
    },
    dataZoom: showZoom ? dataZoomBlock(palette) : undefined,
    // Legend stays at the TOP (scrollable) at every width. The old `media` query flipped it to the bottom on
    // mobile — where it overlapped the dataZoom and, with notMerge, ECharts didn't revert it back to the top
    // when the viewport grew again (it stuck at the bottom). Driven from React instead so it rebuilds cleanly.
    legend: legendBase(palette, isMobile
      ? { textStyle: { color: palette.muted, fontSize: 9, fontFamily: 'ui-monospace,monospace' } }
      : {}),
    tooltip: tooltipBase(palette, {
      confine: true,
      // Render to <body> so the tooltip escapes the card's backdrop-filter stacking context; on Safari it
      // otherwise renders BEHIND the chart ("arkaya düşüyor"). Chrome is unaffected (ECharts offsets to page).
      appendToBody: true,
      formatter: (params) => {
        if (!params?.length) return '';
        // Look up EVERY series' value-in-force at the hovered date from its own data, instead of echarts'
        // `params` (which only includes series with a point near the cursor) — otherwise series that end
        // earlier or sit on a sparser grid silently vanish from the tooltip as the mouse moves.
        const ts = params[0].value[0];
        const date = new Date(ts).toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: 'numeric' });
        const rows = series
          .map((s) => {
            const pt = valueAsOf(s.data, ts);
            return pt ? { name: s.name, color: s._color, val: Number(pt[1]) } : null;
          })
          .filter(Boolean)
          .sort((a, b) => b.val - a.val)
          .map((r) => {
            const val = r.val.toLocaleString('tr-TR', { style: 'currency', currency: displayCurrency, maximumFractionDigits: chartDigits(r.val) });
            return `<div style="display:flex;justify-content:space-between;gap:14px;align-items:center;font-family:ui-monospace,monospace;font-size:11px">
              <span style="display:flex;align-items:center;gap:6px;min-width:0"><span style="width:8px;height:8px;background:${r.color};border-radius:50%;flex-shrink:0"></span><span style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;min-width:0">${r.name}</span></span>
              <span style="font-weight:700;color:${r.color};flex-shrink:0">${val}</span>
            </div>`;
          }).join('');
        return `<div style="padding:6px 4px;min-width:min(200px,calc(100vw - 24px));max-width:calc(100vw - 24px)">
          <div style="font-size:10px;color:${palette.tooltipFg};opacity:0.7;margin-bottom:6px">${date}</div>
          ${rows}
        </div>`;
      },
    }),
    xAxis: timeAxis(palette, { axisLine: { lineStyle: { color: palette.grid } } }),
    yAxis: valueAxis(palette, {
      axisLabel: {
        color: palette.muted, fontSize: 10,
        formatter: (val) => Number(val).toLocaleString('tr-TR', { maximumFractionDigits: chartDigits(Number(val)) }),
      },
    }),
    series,
  };
}
