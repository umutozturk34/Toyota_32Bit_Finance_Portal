import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { cardVariants } from '../../../shared/utils/animations';
import useSessionState from '../../../shared/hooks/useSessionState';
import { TrendingUp } from 'lucide-react';
import ReactECharts from 'echarts-for-react';
import { useTheme } from '../../../shared/context/useTheme';
import { chartPalette } from '../../../shared/charts/echartsTheme';
import { useMoney } from '../../../shared/hooks/useMoney';
import { usePortfolioAllocation } from '../hooks/usePortfolioData';
import Card from '../../../shared/components/card';
import Spinner from '../../../shared/components/feedback/Spinner';
import FilterTabs from '../../../shared/components/form/FilterTabs';
import { ASSET_TYPE_TABS as TYPE_TABS } from '../../../shared/constants/assetTypes';

const GREEN_SHADES = ['#10b981', '#34d399', '#6ee7b7', '#5eead4', '#2dd4bf', '#86efac', '#a7f3d0'];
const RED_SHADES = ['#ef4444', '#f87171', '#fb7185', '#f43f5e', '#fca5a5', '#e11d48', '#fecaca'];

function RealizedPnlChart({ portfolioId, forPrint = false }) {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const { format: money, formatCompact: moneyCompact, currency: displayCurrency, convert } = useMoney();
  const chartRef = useRef(null);
  const [hovered, setHovered] = useState(null);
  const [activeTab, setActiveTab] = useSessionState('portfolio-realized-tab', 'ALL');

  const { data: allData = [] } = usePortfolioAllocation(portfolioId, 'realizedPnl', undefined);
  const { data: typeData = [], isFetching: typeLoading } = usePortfolioAllocation(
    activeTab !== 'ALL' ? portfolioId : null,
    'realizedPnl',
    activeTab !== 'ALL' ? activeTab : undefined,
    6
  );
  // Spinner only when a filtered sub-tab has NO data yet — a background refetch (typeLoading=isFetching) keeps
  // the existing slices visible instead of blanking to a spinner on every sub-tab switch.
  const loading = activeTab !== 'ALL' && typeLoading && !typeData?.length;

  const availableTypes = useMemo(() => new Set((allData || []).map((d) => d.assetType)), [allData]);
  const items = useMemo(() => {
    const source = activeTab === 'ALL' ? allData : typeData;
    return (source || []).filter((d) => Number(d.realizedPnlTry) !== 0);
  }, [activeTab, allData, typeData]);

  const frameBase = displayCurrency === 'USD' || displayCurrency === 'EUR' ? displayCurrency : 'TRY';
  const realizedFor = useCallback((it) => {
    if (frameBase === 'TRY') return Number(it.realizedPnlTry || 0);
    const byCurrency = it.realizedPnlByCurrency || {};
    const frame = byCurrency[frameBase];
    // Backend per-date frame missing (FX history empty for this frame): convert the TRY value at spot
    // so the bar stays in frameBase and is rendered with the matching symbol, instead of stamping the
    // USD/EUR symbol on a raw TRY magnitude.
    return frame != null ? Number(frame) : Number(convert(Number(it.realizedPnlTry || 0), 'TRY'));
  }, [frameBase, convert]);
  // Cost basis on the SAME per-date FX frame as realizedFor: costByCurrency is the backend's per-
  // entry-date conversion, so the USD/EUR cost sits on the same dates as the realized gain beside it
  // instead of today's spot.
  const costFor = useCallback((it) => {
    if (frameBase === 'TRY') return Number(it.costTry || 0);
    const frame = (it.costByCurrency || {})[frameBase];
    return frame != null ? Number(frame) : Number(convert(Number(it.costTry || 0), 'TRY'));
  }, [frameBase, convert]);

  const netPnl = useMemo(
    () => items.reduce((sum, i) => sum + realizedFor(i), 0),
    [items, realizedFor]
  );
  // Slice proportions / weight %s use the TRY magnitude so they stay currency-independent and match the
  // backend's TRY-basis AllocationItem.percent. Displayed amounts still use realizedFor (display currency).
  // Computing weights from the FX-converted amounts made the same data show different slice %s per frame.
  const weightTry = useCallback((it) => Math.abs(Number(it.realizedPnlTry || 0)), []);
  const absTotal = useMemo(
    () => items.reduce((sum, i) => sum + weightTry(i), 0),
    [items, weightTry]
  );

  // SINGLE source of truth for emphasis: the `hovered` slice name, set by BOTH the chart slices (mouseover)
  // and the legend rows (onMouseEnter) — like the fund-detail pie. applyEmphasis syncs ECharts to it (downplay
  // all, then highlight the hovered slice). Driving everything through one state + one applier avoids the
  // imperative-vs-native desync that stuck the donut, AND re-asserts the right state after a notMerge data
  // refetch (which would otherwise preserve a stale highlight). hovered=null → just downplay → neutral.
  const applyEmphasis = useCallback(() => {
    const inst = chartRef.current?.getEchartsInstance?.();
    if (!inst) return;
    inst.dispatchAction({ type: 'downplay', seriesIndex: 0 });
    if (hovered != null) {
      inst.dispatchAction({ type: 'highlight', seriesIndex: 0, name: hovered });
    } else {
      // hideTip clears ECharts' cached pointer (_lastX/_lastY); without it _keepShow re-shows the tooltip at
      // the old position on the next notMerge re-render — the donut "stuck" when leaving via the legend below.
      inst.dispatchAction({ type: 'hideTip' });
    }
  }, [hovered]);
  const labelFor = useCallback((it) => {
    if (it.label === 'OTHER') return t('portfolio.allocation.otherLabel');
    return activeTab === 'ALL'
      ? t(`assets.labels.${it.label}`, { defaultValue: it.label })
      : it.label;
  }, [activeTab, t]);

  const chartEvents = useMemo(() => (forPrint ? {} : {
    mouseover: (params) => setHovered(params?.name ?? null),
    // No clearEmphasis on per-slice mouseout — that fought ECharts' native slice→slice move (it dimmed the
    // slice the cursor was entering). Native handles slice↔slice + slice→empty; globalout + the container
    // onMouseLeave clear only when the cursor actually leaves the whole donut.
    mouseout: () => setHovered(null),
    globalout: () => setHovered(null),
    click: (params) => {
      if (activeTab !== 'ALL') return;
      const it = items.find((i) => i.label === params?.name || labelFor(i) === params?.name);
      if (it && availableTypes.has(it.label)) setActiveTab(it.label);
    },
  }), [forPrint, activeTab, items, labelFor, availableTypes, setActiveTab]);

  const shadeIndex = useMemo(() => {
    const map = new Map();
    let pos = 0;
    let neg = 0;
    items.forEach((it) => {
      const realized = realizedFor(it);
      if (realized >= 0) { map.set(it.label, pos); pos += 1; }
      else { map.set(it.label, neg); neg += 1; }
    });
    return map;
  }, [items, realizedFor]);

  const colorFor = useCallback((it) => {
    const realized = realizedFor(it);
    const idx = shadeIndex.get(it.label) || 0;
    return realized >= 0
      ? GREEN_SHADES[idx % GREEN_SHADES.length]
      : RED_SHADES[idx % RED_SHADES.length];
  }, [shadeIndex, realizedFor]);

  const seriesData = useMemo(() => items.map((it) => {
    const realized = realizedFor(it);
    const cost = costFor(it);
    return {
      name: labelFor(it),
      value: weightTry(it),
      itemStyle: { color: colorFor(it) },
      _cost: cost,
      _realized: realized,
      _weightTry: weightTry(it),
    };
  }), [items, labelFor, colorFor, realizedFor, costFor, weightTry]);

  const palette = chartPalette(isDark);
  const tooltipBg = palette.tooltipBg;
  const tooltipFg = palette.tooltipFg;
  const tooltipBorder = palette.border;
  const labelMuted = isDark ? '#7d8590' : '#636c76';
  const ringStroke = isDark ? '#0d1117' : '#ffffff';

  const totalLabel = activeTab === 'ALL'
    ? t('portfolio.allocation.realizedTotal')
    : t(`assets.labels.${activeTab}`, { defaultValue: activeTab });

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
        const d = params.data || {};
        const realized = d._realized ?? 0;
        const cost = d._cost ?? 0;
        const sign = realized >= 0 ? '+' : '−';
        const color = realized >= 0 ? '#10b981' : '#ef4444';
        const pct = absTotal > 0 ? (((d._weightTry ?? 0) / absTotal) * 100).toFixed(1) : '0.0';
        return `<div style="padding:4px 0;min-width:160px">
            <div style="font-size:11px;color:${tooltipFg};opacity:0.85;margin-bottom:4px">${params.name}</div>
            <div style="font-size:13px;font-family:ui-monospace,monospace;font-weight:700;color:${color}">${sign} ${money(Math.abs(realized), frameBase)}</div>
            <div style="font-size:10px;color:${labelMuted};margin-top:4px">${t('portfolio.allocation.costLabel')}: ${money(cost, frameBase)} · %${pct}</div>
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
        formatter: () => `{label|${totalLabel}}\n{value|${(netPnl >= 0 ? '+' : '−') + moneyCompact(Math.abs(netPnl), frameBase, 100_000)}}`,
        rich: {
          label: { fontSize: 11, color: labelMuted, fontWeight: 500, padding: [0, 0, 4, 0] },
          value: {
            fontSize: 14,
            fontFamily: "'JetBrains Mono', monospace",
            fontWeight: 700,
            color: netPnl >= 0 ? '#10b981' : '#ef4444',
          },
        },
      },
      labelLine: { show: false },
      // focus:'none' (not 'self'): 'self' blurs the other slices AND the center "Net P/L" label, and that blur
      // could get stuck dimmed after a refetch/hover race — leaving the whole donut faded. The hovered slice
      // still emphasizes via applyEmphasis; nothing can stay dimmed.
      emphasis: { scale: false, focus: 'none', label: { show: true } },
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
  }), [seriesData, netPnl, absTotal, tooltipBg, tooltipBorder, tooltipFg, labelMuted, ringStroke, money, moneyCompact, t, totalLabel, frameBase, forPrint]);

  // Sync ECharts' emphasis to `hovered` on every hover change AND after each data refetch (notMerge re-applies
  // the option and would otherwise preserve a stale highlight). One applier, one state — legend-hover and
  // slice-hover both flow through it, so they never desync and the donut never stays stuck at rest.
  useEffect(() => { applyEmphasis(); }, [applyEmphasis, seriesData]);

  return (
    <motion.div variants={cardVariants} initial="hidden" animate="show" className="space-y-4 min-w-0">
      <div className="flex items-center gap-2">
        <TrendingUp className="h-4 w-4 text-accent" />
        <span className="text-sm font-semibold text-fg">{t('portfolio.allocation.realizedTitle')}</span>
      </div>

      <FilterTabs
        items={TYPE_TABS.filter(({ id }) => id !== 'CASH' && id !== 'ALL' && availableTypes.has(id))
          .map(({ id }) => ({ type: id, label: t(`assets.labels.${id}`, { defaultValue: id }) }))}
        activeId={activeTab}
        onSelect={setActiveTab}
        allLabel={t('assets.labels.ALL')}
        showAll
        layoutId="realized-tab"
      />

      <Card variant="elevated" radius="2xl" padding="lg" backdropBlur interactive={false}>
        {loading ? (
          <div className="flex items-center justify-center" style={{ height: 'min(40vh, 260px)', minHeight: 200 }}>
            <Spinner size="md" tone="accent" />
          </div>
        ) : seriesData.length === 0 ? (
          <div className="flex items-center justify-center text-sm text-fg-muted" style={{ height: 'min(40vh, 260px)', minHeight: 200 }}>
            {t('portfolio.allocation.realizedEmpty')}
          </div>
        ) : (
          <div className="space-y-4">
            <div onMouseLeave={() => setHovered(null)}>
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

            <div className="space-y-1.5 max-h-[260px] sm:max-h-[300px] overflow-y-auto" onMouseLeave={() => setHovered(null)}>
              {items.map((it) => {
                const realized = realizedFor(it);
                const cost = costFor(it);
                const pct = absTotal > 0 ? (weightTry(it) / absTotal) * 100 : 0;
                const color = colorFor(it);
                const signColor = realized >= 0 ? '#10b981' : '#ef4444';
                const displayLabel = labelFor(it);
                const isHovered = hovered === displayLabel;
                const clickable = activeTab === 'ALL' && availableTypes.has(it.label);
                return (
                  <div
                    key={it.label}
                    onMouseEnter={() => setHovered(displayLabel)}
                    onClick={() => { if (clickable) setActiveTab(it.label); }}
                    className={`flex items-center gap-3 rounded-lg px-3 py-2 transition-colors ${
                      clickable ? 'cursor-pointer' : 'cursor-default'
                    } ${isHovered ? 'bg-surface/70 ring-1 ring-accent/30' : 'hover:bg-surface/50'}`}
                  >
                    <span className="w-3 h-3 rounded-full shrink-0" style={{ backgroundColor: color }} />
                    <div className="flex-1 min-w-0">
                      <p className="text-xs font-medium text-fg truncate">{displayLabel}</p>
                      <p className="text-[10px] font-mono mt-0.5 truncate" title={`${money(cost, frameBase)} · ${realized >= 0 ? '+' : '−'} ${money(Math.abs(realized), frameBase)}`}>
                        <span className="text-fg-muted">{moneyCompact(cost, frameBase)}</span>
                        <span style={{ color: signColor }}> {realized >= 0 ? '+' : '−'} {moneyCompact(Math.abs(realized), frameBase)}</span>
                      </p>
                    </div>
                    <span className="text-xs font-mono text-fg-muted shrink-0 tabular-nums">{pct.toFixed(1)}%</span>
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

// Memoized for the same reason as AllocationChart: the donut now updates in place (notMerge) instead of
// remounting on theme/currency, so we avoid rebuilding it on unrelated parent re-renders.
export default memo(RealizedPnlChart);
