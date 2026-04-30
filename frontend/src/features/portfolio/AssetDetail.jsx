import { useState } from 'react';
import { motion } from 'framer-motion';
import useSessionState from '../../shared/hooks/useSessionState';
import { ArrowLeft, Hash, DollarSign, BarChart3, Wallet, Calendar, Plus } from 'lucide-react';
import { TrendingUp, TrendingDown, Loader2 } from '../../shared/components/AnimatedIcons';
import Chart from 'react-apexcharts';
import { useTheme } from '../../shared/context/ThemeContext';
import { useAssetSeries } from './usePortfolioData';
import { formatPriceTRY, formatPercent, changeColors, changeBg, getChangeClass } from '../../shared/utils/formatters';
import { cardVariants } from '../../shared/utils/animations';
import { getApexThemeOptions } from '../../shared/utils/apexTheme';
import { PORTFOLIO_RANGES as RANGES, ASSET_TYPE_LABELS } from '../../shared/constants/assetTypes';
import PositionFormModal from './PositionFormModal';

const formatEntryDate = (v) => v ? new Date(v).toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: 'numeric' }) : '—';

const STAT_CARDS = [
  { key: 'quantity', label: 'Miktar', Icon: Hash, format: (v) => Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 6 }) },
  { key: 'entryDate', label: 'Giriş Tarihi', Icon: Calendar, format: formatEntryDate },
  { key: 'entryPrice', label: 'Giriş Fiyatı', Icon: DollarSign, format: formatPriceTRY },
  { key: 'currentPriceTry', label: 'Güncel Fiyat', Icon: BarChart3, format: formatPriceTRY },
  { key: 'marketValueTry', label: 'Piyasa Değeri', Icon: Wallet, format: formatPriceTRY },
];

function AssetChart({ data, isDark }) {
  if (!data || data.length === 0) return null;

  const themeOpts = getApexThemeOptions(isDark);
  const timestamps = data.map(d => new Date(d.timestamp).getTime());
  const marketValues = data.map(d => Number(d.marketValueTry));
  const unitPrices = data.map(d => Number(d.unitPriceTry));

  const options = {
    ...themeOpts,
    chart: {
      ...themeOpts.chart,
      height: 300,
      toolbar: { show: false },
      zoom: { enabled: true },
      animations: { enabled: true, easing: 'easeinout', speed: 600 },
    },
    colors: ['#6366f1', '#f59e0b'],
    stroke: {
      curve: 'smooth',
      width: [2.5, 1.5],
      dashArray: [0, 6],
    },
    fill: {
      type: ['gradient', 'none'],
      gradient: {
        shadeIntensity: 1,
        opacityFrom: 0.35,
        opacityTo: 0.02,
        stops: [0, 100],
      },
    },
    xaxis: {
      ...themeOpts.xaxis,
      type: 'datetime',
      categories: timestamps,
    },
    yaxis: [
      {
        ...themeOpts.yaxis,
        title: { text: '' },
        labels: {
          ...themeOpts.yaxis.labels,
          formatter: (val) => formatPriceTRY(val),
        },
      },
      {
        opposite: true,
        ...themeOpts.yaxis,
        title: { text: '' },
        labels: {
          ...themeOpts.yaxis.labels,
          formatter: (val) => formatPriceTRY(val),
        },
      },
    ],
    tooltip: {
      ...themeOpts.tooltip,
      x: { format: 'dd MMM yyyy' },
    },
    legend: { show: false },
    grid: themeOpts.grid,
    dataLabels: { enabled: false },
  };

  const series = [
    { name: 'Piyasa Değeri', type: 'area', data: marketValues },
    { name: 'Birim Fiyat', type: 'line', data: unitPrices },
  ];

  return <Chart options={options} series={series} type="line" height={300} />;
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
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-1.5">
              <span className="w-3 h-1 rounded bg-[#6366f1]" />
              <span className="text-[11px] text-fg-muted">Piyasa Değeri</span>
            </div>
            <div className="flex items-center gap-1.5">
              <span className="w-3 h-0 border-t-2 border-dashed border-[#f59e0b]" />
              <span className="text-[11px] text-fg-muted">Birim Fiyat</span>
            </div>
          </div>
          <div className="flex gap-1 rounded-lg border border-border-default bg-bg-base p-0.5">
            {RANGES.map(({ id, label }) => (
              <button
                key={id}
                onClick={() => setRange(id)}
                className="relative rounded-md px-2.5 py-1 text-[11px] font-medium transition-all border-none cursor-pointer bg-transparent"
              >
                {range === id && (
                  <motion.span
                    layoutId="asset-range"
                    className="absolute inset-0 rounded-md bg-accent/15"
                    transition={{ type: 'spring', stiffness: 300, damping: 30 }}
                  />
                )}
                <span className={`relative z-10 ${range === id ? 'text-accent' : 'text-fg-muted hover:text-fg'}`}>
                  {label}
                </span>
              </button>
            ))}
          </div>
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
