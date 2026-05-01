import { useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import useSessionState from '../../shared/hooks/useSessionState';
import { ArrowLeft, Hash, DollarSign, BarChart3, Wallet, Calendar, Plus } from 'lucide-react';
import { TrendingUp, TrendingDown, Loader2 } from '../../shared/components/AnimatedIcons';
import ReactECharts from 'echarts-for-react';
import { useTheme } from '../../shared/context/ThemeContext';
import { useAssetSeries } from './usePortfolioData';
import { formatPriceTRY, formatPercent, changeColors, changeBg, getChangeClass } from '../../shared/utils/formatters';
import { cardVariants } from '../../shared/utils/animations';
import { ASSET_TYPE_LABELS } from '../../shared/constants/assetTypes';
import RangeSelector from '../../shared/components/RangeSelector';
import PositionFormModal from './PositionFormModal';

const formatEntryDate = (v) => v ? new Date(v).toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: 'numeric' }) : '—';

const STAT_CARDS = [
  { key: 'quantity', label: 'Miktar', Icon: Hash, format: (v) => Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 6 }) },
  { key: 'entryDate', label: 'Giriş Tarihi', Icon: Calendar, format: formatEntryDate },
  { key: 'entryPrice', label: 'Giriş Fiyatı', Icon: DollarSign, format: formatPriceTRY },
  { key: 'currentPriceTry', label: 'Güncel Fiyat', Icon: BarChart3, format: formatPriceTRY },
  { key: 'marketValueTry', label: 'Piyasa Değeri', Icon: Wallet, format: formatPriceTRY },
];

const LINE_COLOR = '#6366f1';
const UNIT_COLOR = '#f59e0b';

function AssetChart({ data, isDark }) {
  const option = useMemo(() => buildAssetChartOption(data, isDark), [data, isDark]);
  if (!option) return null;
  return <ReactECharts option={option} notMerge lazyUpdate style={{ height: 300 }} opts={{ renderer: 'canvas' }} />;
}

function buildAssetChartOption(data, isDark) {
  if (!data || data.length === 0) return null;

  const muted = isDark ? '#6b6b7a' : '#94a3b8';
  const grid = isDark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.05)';
  const tooltipBg = isDark ? 'rgba(12,12,20,0.95)' : 'rgba(255,255,255,0.97)';
  const tooltipFg = isDark ? '#e2e2ea' : '#1a1a2e';
  const tooltipBorder = isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.08)';

  const seriesData = data.map((d) => ({
    value: [new Date(d.timestamp).getTime(), Number(d.marketValueTry)],
    unitPrice: Number(d.unitPriceTry),
    quantity: Number(d.quantity ?? 0),
  }));

  return {
    backgroundColor: 'transparent',
    animation: data.length < 200,
    grid: { left: 65, right: 24, top: 16, bottom: 30 },
    dataZoom: [
      { type: 'inside', xAxisIndex: 0, filterMode: 'none', zoomOnMouseWheel: true, moveOnMouseMove: 'shift', moveOnMouseWheel: false },
    ],
    tooltip: {
      trigger: 'axis',
      backgroundColor: tooltipBg,
      borderColor: tooltipBorder,
      textStyle: { color: tooltipFg, fontSize: 11 },
      formatter: (params) => {
        const point = params?.[0]?.data;
        if (!point) return '';
        const date = new Date(point.value[0]).toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: 'numeric' });
        const market = formatPriceTRY(point.value[1]);
        const unit = formatPriceTRY(point.unitPrice);
        return `
          <div style="padding:6px 2px;min-width:180px">
            <div style="font-size:10px;color:${tooltipFg};opacity:0.65;margin-bottom:6px">${date}</div>
            <div style="display:flex;justify-content:space-between;gap:14px;font-size:11px;margin-bottom:3px">
              <span style="display:flex;align-items:center;gap:5px;color:${tooltipFg};opacity:0.85"><span style="display:inline-block;width:6px;height:6px;border-radius:999px;background:${LINE_COLOR}"></span>Piyasa Değeri</span>
              <span style="font-family:ui-monospace,monospace;font-weight:600;color:${LINE_COLOR}">${market}</span>
            </div>
            <div style="display:flex;justify-content:space-between;gap:14px;font-size:11px">
              <span style="display:flex;align-items:center;gap:5px;color:${tooltipFg};opacity:0.85"><span style="display:inline-block;width:6px;height:6px;border-radius:999px;background:${UNIT_COLOR}"></span>Birim Fiyat</span>
              <span style="font-family:ui-monospace,monospace;font-weight:600;color:${UNIT_COLOR}">${unit}</span>
            </div>
          </div>`;
      },
    },
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
      axisLabel: { color: muted, fontSize: 10, formatter: (val) => formatPriceTRY(val) },
      splitLine: { lineStyle: { color: grid, type: 'dashed' } },
    },
    series: [{
      name: 'Piyasa Değeri',
      type: 'line',
      smooth: data.length < 200,
      showSymbol: false,
      sampling: 'lttb',
      data: seriesData,
      itemStyle: { color: LINE_COLOR },
      lineStyle: { width: 2.2, color: LINE_COLOR },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: `${LINE_COLOR}55` },
            { offset: 1, color: `${LINE_COLOR}00` },
          ],
        },
      },
    }],
  };
}

export default function AssetDetail({ portfolioId, asset, onBack }) {
  const { isDark } = useTheme();
  const [range, setRange] = useSessionState(`portfolio-asset-range-${asset.assetCode}`, 'ALL');
  const [addLotOpen, setAddLotOpen] = useState(false);

  const { data: series = [], isLoading: loading } = useAssetSeries(
    portfolioId, asset.assetType, asset.assetCode, range
  );

  const pnlClass = getChangeClass(asset.pnlTry);
  const displayLabel = asset.assetCode;
  const displayBadge = asset.assetImage || null;
  const displayBadgeText = asset.assetCode.replace('.IS', '').slice(0, 3).toUpperCase();
  const displaySub = asset.assetName || (ASSET_TYPE_LABELS[asset.assetType] || asset.assetType);

  return (
    <div className="space-y-5">
      <motion.div
        initial={{ opacity: 0, y: -16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
        className="flex items-center justify-between gap-3"
      >
        <div className="flex items-center gap-3">
          <button
            onClick={onBack}
            className="flex items-center justify-center w-9 h-9 rounded-lg border border-border-default bg-bg-elevated text-fg-muted hover:text-fg hover:bg-surface hover:-translate-x-0.5 transition-all cursor-pointer"
          >
            <ArrowLeft className="h-4 w-4" />
          </button>
          <div className="flex items-center gap-3">
            {displayBadge ? (
              <img src={displayBadge} alt={displayLabel} className="w-10 h-10 rounded-xl" />
            ) : (
              <span className="flex items-center justify-center w-10 h-10 rounded-xl bg-accent/10 text-sm font-bold text-accent">
                {displayBadgeText}
              </span>
            )}
            <div>
              <h1 className="text-xl font-bold text-fg">{displayLabel}</h1>
              <p className="text-xs text-fg-muted">{displaySub}</p>
            </div>
          </div>
        </div>
        <button
          onClick={() => setAddLotOpen(true)}
          className="flex items-center gap-2 rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-white hover:bg-accent-bright transition-all border-none cursor-pointer"
        >
          <Plus className="h-4 w-4" />
          Yeni Lot
        </button>
      </motion.div>

      <motion.div
        variants={cardVariants}
        initial="hidden"
        animate="show"
        className="grid grid-cols-2 sm:grid-cols-5 gap-3"
      >
        {STAT_CARDS.map(({ key, label, Icon, format }) => (
          <div key={key} className="rounded-xl border border-border-default bg-bg-elevated p-3 space-y-2 card-hover transition-all duration-200 hover:border-border-hover">
            <div className="flex items-center gap-2">
              <div className="flex items-center justify-center w-6 h-6 rounded-md bg-accent/10">
                <Icon className="h-3 w-3 text-accent" />
              </div>
              <p className="text-[11px] text-fg-muted">{label}</p>
            </div>
            <p className="text-sm font-semibold font-mono text-fg">{format(asset[key])}</p>
          </div>
        ))}
      </motion.div>

      <motion.div
        variants={cardVariants}
        initial="hidden"
        animate="show"
        className={`flex items-center justify-between rounded-xl border p-4 card-hover transition-all duration-200 ${
          asset.pnlTry >= 0
            ? 'border-success/20 bg-success/5 hover:border-success/40'
            : 'border-danger/20 bg-danger/5 hover:border-danger/40'
        }`}
      >
        <div className="flex items-center gap-2">
          {asset.pnlTry >= 0
            ? <TrendingUp className="h-5 w-5 text-success" />
            : <TrendingDown className="h-5 w-5 text-danger" />}
          <span className="text-sm font-medium text-fg">Kar/Zarar</span>
        </div>
        <div className="text-right flex items-center gap-3">
          <span className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-mono font-medium ${changeBg[pnlClass]} ${changeColors[pnlClass]}`}>
            {formatPercent(asset.pnlPercent)}
          </span>
          <p className={`text-lg font-semibold font-mono ${changeColors[pnlClass]}`}>
            {formatPriceTRY(asset.pnlTry)}
          </p>
        </div>
      </motion.div>

      <motion.div
        variants={cardVariants}
        initial="hidden"
        animate="show"
        className="rounded-2xl border border-border-default bg-bg-elevated backdrop-blur-md p-5 space-y-3 card-hover transition-all duration-200 hover:border-border-hover"
      >
        <div className="flex items-center justify-end flex-wrap gap-2">
          <RangeSelector value={range} onChange={setRange} layoutId="asset-range" size="sm" />
        </div>

        <div className="relative">
          {loading && (
            <div className="absolute inset-0 flex items-center justify-center z-10 bg-bg-elevated/60 rounded-lg">
              <Loader2 className="h-6 w-6 animate-spin text-accent" />
            </div>
          )}
          {series.length === 0 && !loading ? (
            <div className="flex items-center justify-center h-[300px] text-sm text-fg-muted">
              Bu aralıkta veri bulunmuyor
            </div>
          ) : series.length > 0 ? (
            <AssetChart data={series} isDark={isDark} />
          ) : null}
        </div>
      </motion.div>

      {addLotOpen && (
        <PositionFormModal
          mode="add"
          portfolioId={portfolioId}
          asset={{
            type: asset.assetType,
            code: asset.assetCode,
            name: asset.assetName,
            image: asset.assetImage,
            currentPrice: asset.currentPriceTry,
          }}
          onClose={() => setAddLotOpen(false)}
          onComplete={() => setAddLotOpen(false)}
        />
      )}
    </div>
  );
}
