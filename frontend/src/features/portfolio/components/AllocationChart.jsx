import { useMemo } from 'react';
import useSessionState from '../../../shared/hooks/useSessionState';
import { PieChart } from 'lucide-react';
import ReactECharts from 'echarts-for-react';
import { Loader2 } from '../../../shared/components/feedback/AnimatedIcons';
import { useTheme } from '../../../shared/context/ThemeContext';
import { formatPriceTRY, formatCompactTRY } from '../../../shared/utils/formatters';
import { usePortfolioAllocation } from '../hooks/usePortfolioData';
import { cardVariants } from '../../../shared/utils/animations';
import {
  ASSET_TYPE_LABELS,
  ASSET_TYPE_CHART_COLORS as ASSET_TYPE_COLORS,
  ASSET_TYPE_TABS as TYPE_TABS,
} from '../../../shared/constants/assetTypes';

const COLORS = [
  '#818cf8', '#34d399', '#fbbf24', '#f87171', '#c084fc',
  '#f472b6', '#22d3ee', '#fb923c', '#2dd4bf', '#a78bfa',
  '#e879f9', '#38bdf8', '#fdba74', '#86efac', '#f9a8d4',
];

export default function AllocationChart({ allocation, portfolioId }) {
  const { isDark } = useTheme();
  const [activeTab, setActiveTab] = useSessionState('portfolio-alloc-tab', 'ALL');

  const { data: assetData, isFetching: assetLoading } = usePortfolioAllocation(
    activeTab !== 'ALL' ? portfolioId : null, 'assetCode', activeTab !== 'ALL' ? activeTab : undefined
  );

  const availableTypes = useMemo(() => {
    if (!allocation) return new Set();
    return new Set(allocation.map((i) => i.label));
  }, [allocation]);

  const loading = activeTab !== 'ALL' && assetLoading;

  const finalData = useMemo(() => {
    const items = activeTab === 'ALL' ? (allocation || []) : (assetData || []);
    return items.filter((i) => Number(i.valueTry) > 0);
  }, [activeTab, allocation, assetData]);

  const totalValue = useMemo(
    () => finalData.reduce((sum, item) => sum + Number(item.valueTry), 0),
    [finalData]
  );

  const seriesData = useMemo(() => finalData.map((item, idx) => {
    const label = activeTab === 'ALL'
      ? (ASSET_TYPE_LABELS[item.label] || item.label)
      : item.label;
    const color = activeTab === 'ALL'
      ? (ASSET_TYPE_COLORS[item.label] || COLORS[0])
      : (COLORS[idx % COLORS.length]);
    return { name: label, value: Number(item.valueTry), itemStyle: { color } };
  }), [finalData, activeTab]);

  const totalLabel = activeTab === 'ALL' ? 'Toplam' : (ASSET_TYPE_LABELS[activeTab] || activeTab);
  const tooltipBg = isDark ? 'rgba(12,12,20,0.95)' : 'rgba(255,255,255,0.97)';
  const tooltipFg = isDark ? '#e2e2ea' : '#1a1a2e';
  const tooltipBorder = isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.08)';
  const labelFg = isDark ? '#e6edf3' : '#1b1f24';
  const labelMuted = isDark ? '#7d8590' : '#636c76';
  const ringStroke = isDark ? '#0d1117' : '#ffffff';

  const option = useMemo(() => ({
    backgroundColor: 'transparent',
    animation: true,
    tooltip: {
      trigger: 'item',
      backgroundColor: tooltipBg,
      borderColor: tooltipBorder,
      textStyle: { color: tooltipFg, fontSize: 11 },
      formatter: (params) => {
        const pct = totalValue > 0 ? ((params.value / totalValue) * 100).toFixed(1) : '0.0';
        return `<div style="padding:4px 0">
          <div style="font-size:11px;color:${tooltipFg};opacity:0.85;margin-bottom:2px">${params.name}</div>
          <div style="font-size:13px;font-family:ui-monospace,monospace;font-weight:700;color:${tooltipFg}">${formatPriceTRY(params.value)}</div>
          <div style="font-size:10px;color:${labelMuted}">%${pct}</div>
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
        formatter: () => `{label|${totalLabel}}\n{value|${formatCompactTRY(totalValue)}}`,
        rich: {
          label: { fontSize: 11, color: labelMuted, fontWeight: 500, padding: [0, 0, 4, 0] },
          value: { fontSize: 14, fontFamily: "'JetBrains Mono', monospace", fontWeight: 700, color: labelFg },
        },
      },
      labelLine: { show: false },
      emphasis: {
        scale: true,
        scaleSize: 4,
        label: { show: true },
      },
      data: seriesData,
    }],
  }), [seriesData, totalValue, totalLabel, tooltipBg, tooltipBorder, tooltipFg, labelFg, labelMuted, ringStroke]);

  return (
    <motion.div variants={cardVariants} initial="hidden" animate="show" className="space-y-4">
      <div className="flex items-center gap-2">
        <PieChart className="h-4 w-4 text-accent" />
        <span className="text-sm font-semibold text-fg">Dağılım</span>
      </div>

      <div className="flex gap-1 rounded-lg border border-border-default bg-bg-elevated p-1 w-fit flex-wrap">
        {TYPE_TABS.map(({ id, label }) => {
          if (id !== 'ALL' && !availableTypes.has(id)) return null;
          return (
            <button
              key={id}
              onClick={() => setActiveTab(id)}
              className="relative rounded-md px-3 py-1.5 text-xs font-medium transition-all border-none cursor-pointer bg-transparent"
            >
              {activeTab === id && (
                <motion.span
                  layoutId="alloc-tab"
                  className="absolute inset-0 rounded-md bg-accent/15"
                  transition={{ type: 'spring', stiffness: 300, damping: 30 }}
                />
              )}
              <span className={`relative z-10 ${activeTab === id ? 'text-accent' : 'text-fg-muted hover:text-fg'}`}>
                {label}
              </span>
            </button>
          );
        })}
      </div>

      <div className="rounded-2xl border border-border-default bg-bg-elevated backdrop-blur-md p-5 card-hover transition-all duration-200 hover:border-border-hover">
        {loading ? (
          <div className="flex items-center justify-center h-80">
            <Loader2 className="h-6 w-6 animate-spin text-accent" />
          </div>
        ) : seriesData.length === 0 ? (
          <div className="flex items-center justify-center h-80 text-sm text-fg-muted">
            Bu türde varlık bulunmuyor
          </div>
        ) : (
          <div className="space-y-4">
            <ReactECharts
              key={`${isDark}-${activeTab}`}
              option={option}
              notMerge
              style={{ height: 220 }}
              opts={{ renderer: 'canvas' }}
            />

            <div className="space-y-1.5">
              {finalData.map((item, idx) => {
                const value = Number(item.valueTry);
                const pct = totalValue > 0 ? (value / totalValue) * 100 : 0;
                const color = activeTab === 'ALL'
                  ? (ASSET_TYPE_COLORS[item.label] || COLORS[0])
                  : (COLORS[idx % COLORS.length]);
                const label = activeTab === 'ALL'
                  ? (ASSET_TYPE_LABELS[item.label] || item.label)
                  : item.label;

                return (
                  <div
                    key={label}
                    className="flex items-center gap-3 rounded-lg px-3 py-2 hover:bg-surface/50 transition-colors"
                  >
                    <span
                      className="w-3 h-3 rounded-full shrink-0"
                      style={{ backgroundColor: color }}
                    />
                    <span className="text-xs font-medium text-fg flex-1 truncate">{label}</span>
                    <span className="text-xs font-mono text-fg-muted">{pct.toFixed(1)}%</span>
                    <span className="text-xs font-mono font-semibold text-fg">{formatPriceTRY(value)}</span>
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </div>
    </motion.div>
  );
}
