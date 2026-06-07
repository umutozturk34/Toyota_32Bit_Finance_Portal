import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { cardVariants } from '../../../shared/utils/animations';
import { largestRemainderPercents } from '../../../shared/utils/percent';
import useSessionState from '../../../shared/hooks/useSessionState';
import { PieChart } from 'lucide-react';
import ReactECharts from 'echarts-for-react';
import { useTheme } from '../../../shared/context/useTheme';
import { chartPalette } from '../../../shared/charts/echartsTheme';
import { useMoney } from '../../../shared/hooks/useMoney';
import { usePortfolioAllocation } from '../hooks/usePortfolioData';
import Card from '../../../shared/components/card';
import Spinner from '../../../shared/components/feedback/Spinner';
import FilterTabs from '../../../shared/components/form/FilterTabs';
import {
  ASSET_TYPE_CHART_COLORS as ASSET_TYPE_COLORS,
  ASSET_TYPE_TABS as TYPE_TABS,
} from '../../../shared/constants/assetTypes';

const COLORS = [
  '#818cf8', '#34d399', '#fbbf24', '#f87171', '#c084fc',
  '#f472b6', '#22d3ee', '#fb923c', '#2dd4bf', '#a78bfa',
  '#e879f9', '#38bdf8', '#fdba74', '#86efac', '#f9a8d4',
];

function AllocationChart({ allocation, portfolioId, forPrint = false }) {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const { format: money, formatCompact: moneyCompact, currency: displayCurrency, convert } = useMoney();
  const [activeTab, setActiveTab] = useSessionState('portfolio-alloc-tab', 'ALL');
  // The CASH (closed-proceeds) slice carries per-entry/exit-date FX frames (costByCurrency /
  // realizedPnlByCurrency); in USD/EUR display use them so the cost+realized breakdown sits on matching
  // FX dates instead of today's spot. TRY/ORIGINAL display and non-cash slices are a no-op.
  const frameBase = displayCurrency === 'USD' || displayCurrency === 'EUR' ? displayCurrency : 'TRY';
  const frameOf = useCallback((item, tryVal, mapKey) => {
    if (frameBase === 'TRY' || tryVal == null) return tryVal;
    const frame = (item[mapKey] || {})[frameBase];
    // Per-date frame missing (FX history empty): convert the TRY value at spot so the slice stays in
    // frameBase rather than a TRY magnitude carrying the USD/EUR symbol.
    return frame != null ? Number(frame) : Number(convert(Number(tryVal), 'TRY'));
  }, [frameBase, convert]);
  // Slice magnitude in the DISPLAY currency so the pie proportions, the centre Total and the legend amounts
  // all sit on the same basis as the summary card. The CASH (closed) slice's display value is its proceeds at
  // the exit-date FX = cost@entry-FX + realized (both per-date frames); open slices convert at today's spot.
  // TRY display is a no-op. Without this the pie was sized in TRY while the labels showed USD/EUR, so the
  // proportions (and the centre total vs the Market Value card) diverged.
  const displayValueOf = useCallback((item) => {
    const vTry = Number(item.valueTry);
    if (frameBase === 'TRY') return vTry;
    // Any bucket carrying per-date frames (CASH proceeds, or an open VIOP's equity) is shown as cost@entry-FX +
    // PnL@per-date, matching the K/Z card. The backend already signs realizedPnlByCurrency for a VIOP SHORT
    // (directionalRealizedFrames), so cost + realized is the DIRECTION-AWARE equity — a profiting SHORT (whose
    // converted notional falls) reads above its cost, not below. Only fall back to a today's-spot conversion of
    // the (already direction-aware) TRY equity when no per-date frames exist for this slice/currency.
    const cost = (item.costByCurrency || {})[frameBase];
    const realized = (item.realizedPnlByCurrency || {})[frameBase];
    if (cost != null && realized != null) return Number(cost) + Number(realized);
    return Number(convert(vTry, 'TRY'));
  }, [frameBase, convert]);
  const assetLabel = useCallback((id) => {
    if (id === 'CASH') return t('portfolio.allocation.closedLabel');
    if (id === 'OTHER') return t('portfolio.allocation.otherLabel');
    return t(`assets.labels.${id}`, { defaultValue: id });
  }, [t]);
  const chartRef = useRef(null);
  const [hoveredSliceName, setHoveredSliceName] = useState(null);

  // SINGLE source of truth for emphasis: `hoveredSliceName`, set by BOTH the chart slices (mouseover) and the
  // legend rows (onMouseEnter) — like the fund-detail pie. applyEmphasis syncs ECharts to it (downplay all,
  // then highlight the hovered slice). One state + one applier avoids the imperative-vs-native desync that
  // stuck the donut, and re-asserts the right state after a notMerge data refetch. null → downplay → neutral.
  const applyEmphasis = useCallback(() => {
    const inst = chartRef.current?.getEchartsInstance?.();
    if (!inst) return;
    inst.dispatchAction({ type: 'downplay', seriesIndex: 0 });
    if (hoveredSliceName != null) {
      inst.dispatchAction({ type: 'highlight', seriesIndex: 0, name: hoveredSliceName });
    } else {
      // hideTip clears the cached pointer so _keepShow can't re-show a stale tooltip on the next re-render.
      inst.dispatchAction({ type: 'hideTip' });
    }
  }, [hoveredSliceName]);
  const chartEvents = useMemo(() => (forPrint ? {} : {
    mouseover: (params) => setHoveredSliceName(params?.name ?? null),
    mouseout: () => setHoveredSliceName(null),
    globalout: () => setHoveredSliceName(null),
  }), [forPrint]);

  const { data: assetData, isFetching: assetLoading } = usePortfolioAllocation(
    activeTab !== 'ALL' ? portfolioId : null,
    'assetCode',
    activeTab !== 'ALL' ? activeTab : undefined,
    6
  );

  const availableTypes = useMemo(() => {
    if (!allocation) return new Set();
    return new Set(allocation.map((i) => i.label));
  }, [allocation]);

  const loading = activeTab !== 'ALL' && assetLoading;

  const finalData = useMemo(() => {
    const items = activeTab === 'ALL' ? (allocation || []) : (assetData || []);
    return items.filter((i) => {
      const value = Number(i.valueTry);
      if (i.label === 'CASH') return value !== 0;
      return value > 0;
    });
  }, [activeTab, allocation, assetData]);

  const totalValue = useMemo(
    () => finalData.reduce((sum, item) => sum + Math.abs(displayValueOf(item)), 0),
    [finalData, displayValueOf]
  );

  // Largest-remainder percentages (sum to exactly 100,0) so the legend doesn't read e.g. 100,1 from
  // independently-rounded shares. Indexed parallel to finalData; reused by both the slices and the legend.
  const displayPercents = useMemo(
    () => largestRemainderPercents(finalData.map((item) => Math.abs(displayValueOf(item))), 1),
    [finalData, displayValueOf]
  );

  const seriesData = useMemo(() => finalData.map((item, idx) => {
    const label = activeTab === 'ALL'
      ? assetLabel(item.label)
      : item.label;
    const isCash = item.label === 'CASH';
    const realized = item.realizedPnlTry != null ? Number(item.realizedPnlTry) : null;
    const color = isCash
      ? (realized != null && realized < 0 ? '#ef4444' : '#10b981')
      : activeTab === 'ALL'
        ? (ASSET_TYPE_COLORS[item.label] || COLORS[0])
        : (COLORS[idx % COLORS.length]);
    return {
      name: label,
      value: Math.abs(displayValueOf(item)),
      itemStyle: { color },
      _cost: frameOf(item, item.costTry != null ? Number(item.costTry) : null, 'costByCurrency'),
      _realized: frameOf(item, realized, 'realizedPnlByCurrency'),
      _pct: displayPercents[idx],
      _isCash: isCash,
    };
  }), [finalData, activeTab, assetLabel, frameOf, displayValueOf, displayPercents]);

  const totalLabel = activeTab === 'ALL' ? t('portfolio.allocation.total') : assetLabel(activeTab);
  const palette = chartPalette(isDark);
  const tooltipBg = palette.tooltipBg;
  const tooltipFg = palette.tooltipFg;
  const tooltipBorder = palette.border;
  const labelFg = isDark ? '#e6edf3' : '#1b1f24';
  const labelMuted = isDark ? '#7d8590' : '#636c76';
  const ringStroke = isDark ? '#0d1117' : '#ffffff';

  const option = useMemo(() => ({
    backgroundColor: 'transparent',
    animation: !forPrint,
    tooltip: forPrint ? { show: false } : {
      trigger: 'item',
      appendToBody: true,
      confine: false,
      backgroundColor: tooltipBg,
      borderColor: tooltipBorder,
      textStyle: { color: tooltipFg, fontSize: 11 },
      extraCssText: 'z-index:9999;box-shadow:0 8px 24px rgba(0,0,0,0.25);',
      formatter: (params) => {
        const pct = Number(params.data?._pct ?? (totalValue > 0 ? (params.value / totalValue) * 100 : 0)).toFixed(1);
        const data = params.data || {};
        let breakdown = '';
        if (data._isCash && data._cost != null && data._realized != null) {
          const realized = data._realized;
          const sign = realized >= 0 ? '+' : '−';
          const realizedColor = realized >= 0 ? '#10b981' : '#ef4444';
          breakdown = `<div style="margin-top:6px;padding-top:6px;border-top:1px solid ${tooltipBorder};font-size:11px;font-family:ui-monospace,monospace;color:${tooltipFg}">
              ${money(data._cost, frameBase)} <span style="color:${realizedColor}">${sign} ${money(Math.abs(realized), frameBase)}</span>
            </div>`;
        }
        return `<div style="padding:4px 0">
          <div style="font-size:11px;color:${tooltipFg};opacity:0.85;margin-bottom:2px">${params.name}</div>
          <div style="font-size:13px;font-family:ui-monospace,monospace;font-weight:700;color:${tooltipFg}">${money(params.value, frameBase)}</div>
          <div style="font-size:10px;color:${labelMuted}">%${pct}</div>
          ${breakdown}
        </div>`;
      },
    },
    series: [{
      type: 'pie',
      radius: ['58%', '80%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: ringStroke, borderWidth: 3 },
      label: {
        show: true,
        position: 'center',
        formatter: () => `{label|${totalLabel}}\n{value|${moneyCompact(totalValue, frameBase)}}`,
        rich: {
          label: { fontSize: 11, color: labelMuted, fontWeight: 500, padding: [0, 0, 4, 0] },
          value: { fontSize: 14, fontFamily: "'JetBrains Mono', monospace", fontWeight: 700, color: labelFg },
        },
      },
      labelLine: { show: false },
      emphasis: {
        scale: false,
        focus: 'self',
        label: { show: true },
      },
      data: seriesData,
    }],
    media: [{
      query: { maxWidth: 640 },
      option: {
        series: [{
          radius: ['50%', '74%'],
          itemStyle: { borderWidth: 2 },
          label: {
            rich: {
              label: { fontSize: 10, padding: [0, 0, 3, 0] },
              value: { fontSize: 12 },
            },
          },
        }],
      },
    }],
  }), [seriesData, totalValue, totalLabel, tooltipBg, tooltipBorder, tooltipFg, labelFg, labelMuted, ringStroke, money, moneyCompact, frameBase, forPrint]);

  // Sync ECharts' emphasis to `hoveredSliceName` on hover change AND after each data refetch (notMerge would
  // otherwise preserve a stale highlight). One state, one applier — legend-hover and slice-hover never desync.
  useEffect(() => { applyEmphasis(); }, [applyEmphasis, seriesData]);

  return (
    <motion.div variants={cardVariants} initial="hidden" animate="show" className="space-y-4 min-w-0">
      <div className="flex items-center gap-2">
        <PieChart className="h-4 w-4 text-accent" />
        <span className="text-sm font-semibold text-fg">{t('portfolio.allocation.title')}</span>
      </div>

      <FilterTabs
        items={TYPE_TABS.filter(({ id }) => id !== 'ALL' && availableTypes.has(id))
          .map(({ id }) => ({ type: id, label: assetLabel(id) }))}
        activeId={activeTab}
        onSelect={setActiveTab}
        allLabel={assetLabel('ALL')}
        showAll
        layoutId="alloc-tab"
      />

      <Card variant="elevated" radius="2xl" padding="lg" backdropBlur interactive={false}>
        {loading ? (
          <div className="flex items-center justify-center" style={{ height: 'min(40vh, 260px)', minHeight: 200 }}>
            <Spinner size="md" tone="accent" />
          </div>
        ) : seriesData.length === 0 ? (
          <div className="flex items-center justify-center text-sm text-fg-muted" style={{ height: 'min(40vh, 260px)', minHeight: 200 }}>
            {t('portfolio.allocation.empty')}
          </div>
        ) : (
          <div className="space-y-4">
            <div onMouseLeave={() => setHoveredSliceName(null)}>
              <ReactECharts
                ref={chartRef}
                key={forPrint ? 'print' : 'screen'}
                option={option}
                notMerge
                lazyUpdate
                style={forPrint ? { height: 260, width: '100%', pointerEvents: 'none' } : { height: 'min(40vh, 260px)', minHeight: 200, width: '100%' }}
                opts={{ renderer: forPrint ? 'svg' : 'canvas' }}
                onEvents={chartEvents}
              />
            </div>

            <div className="space-y-1.5 max-h-[260px] sm:max-h-[300px] overflow-y-auto" onMouseLeave={() => setHoveredSliceName(null)}>
              {finalData.map((item, idx) => {
                const value = displayValueOf(item);
                const pct = displayPercents[idx] ?? 0;
                const isCash = item.label === 'CASH';
                const cost = frameOf(item, item.costTry != null ? Number(item.costTry) : null, 'costByCurrency');
                const realized = frameOf(item, item.realizedPnlTry != null ? Number(item.realizedPnlTry) : null, 'realizedPnlByCurrency');
                const color = isCash
                  ? (realized != null && realized < 0 ? '#ef4444' : '#10b981')
                  : activeTab === 'ALL'
                    ? (ASSET_TYPE_COLORS[item.label] || COLORS[0])
                    : (COLORS[idx % COLORS.length]);
                const label = activeTab === 'ALL'
                  ? assetLabel(item.label)
                  : item.label;

                const isHovered = hoveredSliceName === label;
                const showBreakdown = isCash && cost != null && realized != null;
                return (
                  <div
                    key={label}
                    onMouseEnter={() => setHoveredSliceName(label)}
                    className={`flex items-center gap-3 rounded-lg px-3 py-2 cursor-default transition-colors ${
                      isHovered ? 'bg-surface/70 ring-1 ring-accent/30' : 'hover:bg-surface/50'
                    }`}
                  >
                    <span
                      className="w-3 h-3 rounded-full shrink-0"
                      style={{ backgroundColor: color }}
                    />
                    <div className="flex-1 min-w-0">
                      <p className="text-xs font-medium text-fg truncate">{label}</p>
                      {showBreakdown && (
                        <p className="text-[10px] font-mono mt-0.5">
                          <span className="text-fg-muted">{money(cost, frameBase)}</span>
                          <span className={realized >= 0 ? 'text-success' : 'text-danger'}>
                            {' '}{realized >= 0 ? '+' : '−'} {money(Math.abs(realized), frameBase)}
                          </span>
                        </p>
                      )}
                    </div>
                    <span className="text-xs font-mono text-fg-muted shrink-0 tabular-nums">{pct.toFixed(1)}%</span>
                    {!showBreakdown && (
                      <span className={`text-xs font-mono font-semibold shrink-0 ${isCash ? (realized != null && realized < 0 ? 'text-danger' : 'text-success') : 'text-fg'}`}>{money(value, frameBase)}</span>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </Card>
    </motion.div>
  );
}

// Memoized: with the ECharts key no longer keyed on theme/currency, the donut updates in place via notMerge,
// so blocking parent-churn re-renders (stable `allocation` ref) keeps the canvas from rebuilding on first load.
export default memo(AllocationChart);
