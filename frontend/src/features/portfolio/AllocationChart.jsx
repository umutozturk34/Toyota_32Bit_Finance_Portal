import { useMemo } from 'react';
import useSessionState from "../../shared/hooks/useSessionState";
import { motion } from 'framer-motion';
import { PieChart } from 'lucide-react';
import { Loader2 } from '../../shared/components/AnimatedIcons';
import Chart from 'react-apexcharts';
import { useTheme } from '../../shared/context/ThemeContext';
import { formatPriceTRY, formatCompactTRY } from '../../shared/utils/formatters';
import { getApexThemeOptions } from '../../shared/utils/apexTheme';
import { usePortfolioAllocation } from './usePortfolioData';
import { cardVariants } from '../../shared/utils/animations';
import {
  ASSET_TYPE_LABELS,
  ASSET_TYPE_CHART_COLORS as ASSET_TYPE_COLORS,
  ASSET_TYPE_TABS as TYPE_TABS,
} from '../../shared/constants/assetTypes';

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
    return new Set(allocation.map(i => i.label));
  }, [allocation]);

  const loading = activeTab !== 'ALL' && assetLoading;

  const finalData = useMemo(() => {
    let items;
    if (activeTab === 'ALL') {
      items = allocation || [];
    } else {
      if (!assetData) return [];
      items = assetData;
    }
    return items.filter(i => Number(i.valueTry) > 0);
  }, [activeTab, allocation, assetData]);

  const labels = useMemo(() =>
    finalData.map(item =>
      activeTab === 'ALL'
        ? (ASSET_TYPE_LABELS[item.label] || item.label)
        : item.label
    ),
  [finalData, activeTab]);

  const series = useMemo(() =>
    finalData.map(item => Number(item.valueTry)),
  [finalData]);

  const totalValue = useMemo(() =>
    series.reduce((sum, val) => sum + val, 0),
  [series]);

  const chartColors = useMemo(() => {
    if (activeTab === 'ALL') {
      return finalData.map(item => ASSET_TYPE_COLORS[item.label] || COLORS[0]);
    }
    return COLORS.slice(0, labels.length);
  }, [activeTab, finalData, labels.length]);

  const baseTheme = getApexThemeOptions(isDark);

  const options = useMemo(() => ({
    ...baseTheme,
    chart: {
      ...baseTheme.chart,
      type: 'donut',
      animations: {
        enabled: true,
        easing: 'easeinout',
        speed: 800,
        dynamicAnimation: { enabled: true, speed: 400 },
        animateGradually: { enabled: true, delay: 100 },
      },
    },
    labels,
    colors: chartColors,
    plotOptions: {
      pie: {
        expandOnClick: true,
        donut: {
          size: '72%',
          labels: {
            show: true,
            name: {
              show: true,
              fontSize: '13px',
              fontFamily: "'Nunito Sans', sans-serif",
              fontWeight: 600,
              color: isDark ? '#e6edf3' : '#1b1f24',
              offsetY: -4,
            },
            value: {
              show: true,
              fontSize: '14px',
              fontFamily: "'JetBrains Mono', monospace",
              fontWeight: 700,
              color: isDark ? '#e6edf3' : '#1b1f24',
              offsetY: 4,
              formatter: (val) => formatCompactTRY(Number(val)),
            },
            total: {
              show: true,
              showAlways: true,
              label: activeTab === 'ALL' ? 'Toplam' : (ASSET_TYPE_LABELS[activeTab] || activeTab),
              fontSize: '11px',
              fontFamily: "'Nunito Sans', sans-serif",
              fontWeight: 500,
              color: isDark ? '#7d8590' : '#636c76',
              formatter: (w) => formatCompactTRY(
                w.globals.seriesTotals.reduce((a, b) => a + b, 0)
              ),
            },
          },
        },
      },
    },
    dataLabels: {
      enabled: false,
    },
    legend: {
      show: false,
    },
    stroke: {
      show: true,
      width: 3,
      colors: [isDark ? '#0d1117' : '#ffffff'],
    },
    tooltip: {
      ...baseTheme.tooltip,
      y: {
        formatter: (val) => `${formatPriceTRY(val)} (${((val / totalValue) * 100).toFixed(1)}%)`,
      },
    },
    states: {
      hover: { filter: { type: 'darken', value: 0.15 } },
      active: { filter: { type: 'darken', value: 0.25 } },
    },
    responsive: [{
      breakpoint: 768,
      options: {
        chart: { height: 170 },
      },
    }],
  }), [isDark, labels, activeTab, baseTheme, chartColors, totalValue]);

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
        {loading && activeTab !== 'ALL' ? (
          <div className="flex items-center justify-center h-80">
            <Loader2 className="h-6 w-6 animate-spin text-accent" />
          </div>
        ) : series.length === 0 ? (
          <div className="flex items-center justify-center h-80 text-sm text-fg-muted">
            Bu türde varlık bulunmuyor
          </div>
        ) : (
          <div className="space-y-4">
            <Chart
              key={`${isDark}-${activeTab}`}
              options={options}
              series={series}
              type="donut"
              height={200}
            />

            <div className="space-y-1.5">
              {finalData.map((item, idx) => {
                const value = Number(item.valueTry);
                const pct = totalValue > 0 ? (value / totalValue) * 100 : 0;
                const color = chartColors[idx] || COLORS[0];
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
