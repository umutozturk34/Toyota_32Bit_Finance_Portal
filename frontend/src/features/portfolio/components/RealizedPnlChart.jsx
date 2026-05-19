import { useCallback, useMemo, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { cardVariants } from '../../../shared/utils/animations';
import useSessionState from '../../../shared/hooks/useSessionState';
import { TrendingUp } from 'lucide-react';
import ReactECharts from 'echarts-for-react';
import { useTheme } from '../../../shared/context/useTheme';
import { useMoney } from '../../../shared/hooks/useMoney';
import { usePortfolioAllocation } from '../hooks/usePortfolioData';
import Card from '../../../shared/components/card';
import Spinner from '../../../shared/components/feedback/Spinner';
import FilterTabs from '../../../shared/components/form/FilterTabs';
import { ASSET_TYPE_TABS as TYPE_TABS } from '../../../shared/constants/assetTypes';

const GREEN_SHADES = ['#10b981', '#34d399', '#6ee7b7', '#5eead4', '#2dd4bf', '#86efac', '#a7f3d0'];
const RED_SHADES = ['#ef4444', '#f87171', '#fb7185', '#f43f5e', '#fca5a5', '#e11d48', '#fecaca'];

export default function RealizedPnlChart({ portfolioId }) {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const { format: money, formatCompact: moneyCompact } = useMoney();
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
  const loading = activeTab !== 'ALL' && typeLoading;

  const availableTypes = useMemo(() => new Set((allData || []).map((d) => d.assetType)), [allData]);
  const items = useMemo(() => {
    const source = activeTab === 'ALL' ? allData : typeData;
    return (source || []).filter((d) => Number(d.realizedPnlTry) !== 0);
  }, [activeTab, allData, typeData]);

  const netPnl = useMemo(
    () => items.reduce((sum, i) => sum + Number(i.realizedPnlTry || 0), 0),
    [items]
  );
  const absTotal = useMemo(
    () => items.reduce((sum, i) => sum + Math.abs(Number(i.realizedPnlTry || 0)), 0),
    [items]
  );

  const highlight = useCallback((name) => {
    const inst = chartRef.current?.getEchartsInstance?.();
    if (inst) inst.dispatchAction({ type: 'highlight', seriesIndex: 0, name });
  }, []);
  const downplay = useCallback((name) => {
    const inst = chartRef.current?.getEchartsInstance?.();
    if (inst) inst.dispatchAction({ type: 'downplay', seriesIndex: 0, name });
  }, []);
  const labelFor = useCallback((it) => {
    if (it.label === 'OTHER') return t('portfolio.allocation.otherLabel');
    return activeTab === 'ALL'
      ? t(`assets.labels.${it.label}`, { defaultValue: it.label })
      : it.label;
  }, [activeTab, t]);

  const chartEvents = useMemo(() => ({
    mouseover: (params) => setHovered(params?.name ?? null),
    mouseout: () => setHovered(null),
    click: (params) => {
      if (activeTab !== 'ALL') return;
      const it = items.find((i) => i.label === params?.name || labelFor(i) === params?.name);
      if (it && availableTypes.has(it.label)) setActiveTab(it.label);
    },
  }), [activeTab, items, labelFor, availableTypes, setActiveTab]);

  const shadeIndex = useMemo(() => {
    const map = new Map();
    let pos = 0;
    let neg = 0;
    items.forEach((it) => {
      const realized = Number(it.realizedPnlTry || 0);
      if (realized >= 0) { map.set(it.label, pos); pos += 1; }
      else { map.set(it.label, neg); neg += 1; }
    });
    return map;
  }, [items]);

  const colorFor = useCallback((it) => {
    const realized = Number(it.realizedPnlTry || 0);
    const idx = shadeIndex.get(it.label) || 0;
    return realized >= 0
      ? GREEN_SHADES[idx % GREEN_SHADES.length]
      : RED_SHADES[idx % RED_SHADES.length];
  }, [shadeIndex]);

  const seriesData = useMemo(() => items.map((it) => {
    const realized = Number(it.realizedPnlTry || 0);
    const cost = Number(it.costTry || 0);
    return {
      name: labelFor(it),
      value: Math.abs(realized),
      itemStyle: { color: colorFor(it) },
      _cost: cost,
      _realized: realized,
    };
  }), [items, labelFor, colorFor]);

  const tooltipBg = isDark ? 'rgba(12,12,20,0.95)' : 'rgba(255,255,255,0.97)';
  const tooltipFg = isDark ? '#e2e2ea' : '#1a1a2e';
  const tooltipBorder = isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.08)';
  const labelMuted = isDark ? '#7d8590' : '#636c76';
  const ringStroke = isDark ? '#0d1117' : '#ffffff';

  const totalLabel = activeTab === 'ALL'
    ? t('portfolio.allocation.realizedTotal')
    : t(`assets.labels.${activeTab}`, { defaultValue: activeTab });

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
        const d = params.data || {};
        const realized = d._realized ?? 0;
        const cost = d._cost ?? 0;
        const sign = realized >= 0 ? '+' : '−';
        const color = realized >= 0 ? '#10b981' : '#ef4444';
        const pct = absTotal > 0 ? ((Math.abs(realized) / absTotal) * 100).toFixed(1) : '0.0';
        return `<div style="padding:4px 0;min-width:160px">
            <div style="font-size:11px;color:${tooltipFg};opacity:0.85;margin-bottom:4px">${params.name}</div>
            <div style="font-size:13px;font-family:ui-monospace,monospace;font-weight:700;color:${color}">${sign} ${money(Math.abs(realized))}</div>
            <div style="font-size:10px;color:${labelMuted};margin-top:4px">${t('portfolio.allocation.costLabel')}: ${money(cost)} · %${pct}</div>
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
        formatter: () => `{label|${totalLabel}}\n{value|${(netPnl >= 0 ? '+' : '−') + moneyCompact(Math.abs(netPnl))}}`,
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
      emphasis: { scale: true, scaleSize: 4, label: { show: true } },
      data: seriesData,
    }],
  }), [seriesData, netPnl, absTotal, tooltipBg, tooltipBorder, tooltipFg, labelMuted, ringStroke, money, moneyCompact, t, totalLabel]);

  return (
    <motion.div variants={cardVariants} initial="hidden" animate="show" className="space-y-4">
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
          <div className="flex items-center justify-center h-80">
            <Spinner size="md" tone="accent" />
          </div>
        ) : seriesData.length === 0 ? (
          <div className="flex items-center justify-center h-80 text-sm text-fg-muted">
            {t('portfolio.allocation.realizedEmpty')}
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
              {items.map((it) => {
                const realized = Number(it.realizedPnlTry || 0);
                const cost = Number(it.costTry || 0);
                const pct = absTotal > 0 ? (Math.abs(realized) / absTotal) * 100 : 0;
                const color = colorFor(it);
                const signColor = realized >= 0 ? '#10b981' : '#ef4444';
                const displayLabel = labelFor(it);
                const isHovered = hovered === displayLabel;
                const clickable = activeTab === 'ALL' && availableTypes.has(it.label);
                return (
                  <div
                    key={it.label}
                    onMouseEnter={() => highlight(displayLabel)}
                    onMouseLeave={() => downplay(displayLabel)}
                    onClick={() => { if (clickable) setActiveTab(it.label); }}
                    className={`flex items-center gap-3 rounded-lg px-3 py-2 transition-colors ${
                      clickable ? 'cursor-pointer' : 'cursor-default'
                    } ${isHovered ? 'bg-surface/70 ring-1 ring-accent/30' : 'hover:bg-surface/50'}`}
                  >
                    <span className="w-3 h-3 rounded-full shrink-0" style={{ backgroundColor: color }} />
                    <div className="flex-1 min-w-0">
                      <p className="text-xs font-medium text-fg truncate">{displayLabel}</p>
                      <p className="text-[10px] font-mono mt-0.5">
                        <span className="text-fg-muted">{money(cost)}</span>
                        <span style={{ color: signColor }}> {realized >= 0 ? '+' : '−'} {money(Math.abs(realized))}</span>
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
