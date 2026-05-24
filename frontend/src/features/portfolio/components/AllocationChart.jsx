import { useCallback, useMemo, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { cardVariants } from '../../../shared/utils/animations';
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

export default function AllocationChart({ allocation, portfolioId }) {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const { format: money, formatCompact: moneyCompact } = useMoney();
  const [activeTab, setActiveTab] = useSessionState('portfolio-alloc-tab', 'ALL');
  const assetLabel = useCallback((id) => {
    if (id === 'CASH') return t('portfolio.allocation.closedLabel');
    if (id === 'OTHER') return t('portfolio.allocation.otherLabel');
    return t(`assets.labels.${id}`, { defaultValue: id });
  }, [t]);
  const chartRef = useRef(null);
  const [hoveredSliceName, setHoveredSliceName] = useState(null);

  const highlightSlice = useCallback((name) => {
    const inst = chartRef.current?.getEchartsInstance?.();
    if (inst) inst.dispatchAction({ type: 'highlight', seriesIndex: 0, name });
  }, []);
  const downplaySlice = useCallback((name) => {
    const inst = chartRef.current?.getEchartsInstance?.();
    if (inst) inst.dispatchAction({ type: 'downplay', seriesIndex: 0, name });
  }, []);
  const chartEvents = useMemo(() => ({
    mouseover: (params) => setHoveredSliceName(params?.name ?? null),
    mouseout: () => setHoveredSliceName(null),
  }), []);

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
    () => finalData.reduce((sum, item) => sum + Math.abs(Number(item.valueTry)), 0),
    [finalData]
  );

  const seriesData = useMemo(() => finalData.map((item, idx) => {
    const label = activeTab === 'ALL'
      ? assetLabel(item.label)
      : item.label;
    const value = Number(item.valueTry);
    const isCash = item.label === 'CASH';
    const realized = item.realizedPnlTry != null ? Number(item.realizedPnlTry) : null;
    const color = isCash
      ? (realized != null && realized < 0 ? '#ef4444' : '#10b981')
      : activeTab === 'ALL'
        ? (ASSET_TYPE_COLORS[item.label] || COLORS[0])
        : (COLORS[idx % COLORS.length]);
    return {
      name: label,
      value: Math.abs(value),
      itemStyle: { color },
      _cost: item.costTry != null ? Number(item.costTry) : null,
      _realized: realized,
      _isCash: isCash,
    };
  }), [finalData, activeTab, assetLabel]);

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
    animation: true,
    tooltip: {
      trigger: 'item',
      appendToBody: true,
      confine: false,
      backgroundColor: tooltipBg,
      borderColor: tooltipBorder,
      textStyle: { color: tooltipFg, fontSize: 11 },
      extraCssText: 'z-index:9999;box-shadow:0 8px 24px rgba(0,0,0,0.25);',
      formatter: (params) => {
        const pct = totalValue > 0 ? ((params.value / totalValue) * 100).toFixed(1) : '0.0';
        const data = params.data || {};
        let breakdown = '';
        if (data._isCash && data._cost != null && data._realized != null) {
          const realized = data._realized;
          const sign = realized >= 0 ? '+' : '−';
          const realizedColor = realized >= 0 ? '#10b981' : '#ef4444';
          breakdown = `<div style="margin-top:6px;padding-top:6px;border-top:1px solid ${tooltipBorder};font-size:11px;font-family:ui-monospace,monospace;color:${tooltipFg}">
              ${money(data._cost)} <span style="color:${realizedColor}">${sign} ${money(Math.abs(realized))}</span>
            </div>`;
        }
        return `<div style="padding:4px 0">
          <div style="font-size:11px;color:${tooltipFg};opacity:0.85;margin-bottom:2px">${params.name}</div>
          <div style="font-size:13px;font-family:ui-monospace,monospace;font-weight:700;color:${tooltipFg}">${money(params.value)}</div>
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
        formatter: () => `{label|${totalLabel}}\n{value|${moneyCompact(totalValue)}}`,
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
  }), [seriesData, totalValue, totalLabel, tooltipBg, tooltipBorder, tooltipFg, labelFg, labelMuted, ringStroke, money, moneyCompact]);

  return (
    <motion.div variants={cardVariants} initial="hidden" animate="show" className="space-y-4">
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
          <div className="flex items-center justify-center h-80">
            <Spinner size="md" tone="accent" />
          </div>
        ) : seriesData.length === 0 ? (
          <div className="flex items-center justify-center h-80 text-sm text-fg-muted">
            {t('portfolio.allocation.empty')}
          </div>
        ) : (
          <div className="space-y-4">
            <ReactECharts
              ref={chartRef}
              key={`${isDark}-${activeTab}`}
              option={option}
              notMerge
              style={{ height: 220 }}
              opts={{ renderer: 'canvas' }}
              onEvents={chartEvents}
            />

            <div className="space-y-1.5">
              {finalData.map((item, idx) => {
                const value = Number(item.valueTry);
                const absValue = Math.abs(value);
                const pct = totalValue > 0 ? (absValue / totalValue) * 100 : 0;
                const isCash = item.label === 'CASH';
                const cost = item.costTry != null ? Number(item.costTry) : null;
                const realized = item.realizedPnlTry != null ? Number(item.realizedPnlTry) : null;
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
                    onMouseEnter={() => highlightSlice(label)}
                    onMouseLeave={() => downplaySlice(label)}
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
                          <span className="text-fg-muted">{money(cost)}</span>
                          <span className={realized >= 0 ? 'text-success' : 'text-danger'}>
                            {' '}{realized >= 0 ? '+' : '−'} {money(Math.abs(realized))}
                          </span>
                        </p>
                      )}
                    </div>
                    <span className="text-xs font-mono text-fg-muted shrink-0 tabular-nums">{pct.toFixed(1)}%</span>
                    {!showBreakdown && (
                      <span className={`text-xs font-mono font-semibold shrink-0 ${isCash ? (realized != null && realized < 0 ? 'text-danger' : 'text-success') : 'text-fg'}`}>{money(value)}</span>
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
