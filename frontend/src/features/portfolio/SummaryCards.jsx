import { motion } from 'framer-motion';
import useSessionState from '../../shared/hooks/useSessionState';
import { Wallet, Banknote, BarChart3, CheckCircle2 } from 'lucide-react';
import { TrendingUp, TrendingDown, AlertCircle } from '../../shared/components/AnimatedIcons';
import { formatPriceTRY, formatPercent, changeColors, changeBg, getChangeClass } from '../../shared/utils/formatters';
import { containerVariants, cardVariants } from '../../shared/utils/animations';
import { usePortfolioSummary } from './usePortfolioData';
import { ASSET_TYPE_FILTERS as SUMMARY_FILTERS } from '../../shared/constants/assetTypes';

const valueCards = [
  { key: 'totalValueTry', label: 'Toplam Değer', Icon: Wallet, iconBg: 'bg-accent/10', iconColor: 'text-accent', border: 'border-t-accent' },
  { key: 'totalCostTry', label: 'Toplam Maliyet', Icon: BarChart3, iconBg: 'bg-fg-muted/10', iconColor: 'text-fg-muted', border: 'border-t-fg-muted' },
  { key: 'cashBalanceTry', label: 'Nakit Bakiye', Icon: Banknote, iconBg: 'bg-warning/10', iconColor: 'text-warning', border: 'border-t-warning' },
];

function PnlCard({ label, value, icon: Icon, showPercent, percent }) {
  const cls = getChangeClass(value);
  const border = value >= 0 ? 'border-t-success' : 'border-t-danger';
  return (
    <motion.div
      variants={cardVariants}
      className={`rounded-xl border border-border-default border-t-2 ${border} bg-bg-elevated p-4 space-y-3 card-hover transition-all duration-200 hover:border-border-hover`}
    >
      <div className="flex items-center gap-2.5">
        <div className={`flex items-center justify-center w-8 h-8 rounded-lg ${value >= 0 ? 'bg-success/10' : 'bg-danger/10'}`}>
          <Icon className={`h-4 w-4 ${value >= 0 ? 'text-success' : 'text-danger'}`} />
        </div>
        <span className="text-xs text-fg-muted font-medium">{label}</span>
      </div>
      <p className={`text-lg font-semibold font-mono ${changeColors[cls]}`}>
        {formatPriceTRY(value)}
      </p>
      {showPercent && (
        <span className={`inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-medium font-mono ${changeBg[cls]} ${changeColors[cls]}`}>
          {formatPercent(percent)}
        </span>
      )}
    </motion.div>
  );
}

export default function SummaryCards({ summary: initialSummary, portfolioId }) {
  const [activeFilter, setActiveFilter] = useSessionState('portfolio-summary-filter', null);

  const { data: filteredSummary, isFetching: loading } = usePortfolioSummary(
    portfolioId, activeFilter
  );

  const summary = activeFilter ? (filteredSummary ?? initialSummary) : initialSummary;

  return (
    <motion.div
      variants={containerVariants(0.06)}
      initial="hidden"
      animate="show"
      className="space-y-3"
    >
      <div className="flex items-center justify-between">
        <div className="flex gap-0.5 rounded-lg border border-border-default bg-bg-elevated p-0.5">
          {SUMMARY_FILTERS.map(({ id, label }) => (
            <button
              key={id || 'all'}
              onClick={() => setActiveFilter(id)}
              className="relative rounded-md px-2.5 py-1 text-[11px] font-medium transition-all border-none cursor-pointer bg-transparent"
            >
              {activeFilter === id && (
                <motion.span
                  layoutId="summary-filter"
                  className="absolute inset-0 rounded-md bg-accent/15"
                  transition={{ type: 'spring', stiffness: 300, damping: 30 }}
                />
              )}
              <span className={`relative z-10 ${activeFilter === id ? 'text-accent' : 'text-fg-muted hover:text-fg'}`}>
                {label}
              </span>
            </button>
          ))}
        </div>
        {loading && (
          <span className="text-[10px] text-fg-muted animate-pulse">Güncelleniyor...</span>
        )}
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        {valueCards.map(({ key, label, Icon, iconBg, iconColor, border }) => {
          const hidden = activeFilter && key === 'cashBalanceTry';
          if (hidden) return null;
          return (
            <motion.div
              key={key}
              variants={cardVariants}
              className={`rounded-xl border border-border-default border-t-2 ${border} bg-bg-elevated p-4 space-y-3 card-hover transition-all duration-200 hover:border-border-hover`}
            >
              <div className="flex items-center gap-2.5">
                <div className={`flex items-center justify-center w-8 h-8 rounded-lg ${iconBg}`}>
                  <Icon className={`h-4 w-4 ${iconColor}`} />
                </div>
                <span className="text-xs text-fg-muted font-medium">{label}</span>
              </div>
              <p className="text-lg font-semibold font-mono text-fg">
                {formatPriceTRY(summary[key])}
              </p>
            </motion.div>
          );
        })}
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        <PnlCard
          label="Açık K/Z"
          value={summary.unrealizedPnlTry ?? summary.totalPnlTry}
          icon={summary.unrealizedPnlTry >= 0 ? TrendingUp : TrendingDown}
        />
        <PnlCard
          label="Toplam K/Z"
          value={summary.totalPnlTry}
          icon={summary.totalPnlTry >= 0 ? TrendingUp : AlertCircle}
          showPercent
          percent={summary.pnlPercent}
        />
        {!activeFilter && (
          <PnlCard
            label="Gerçekleşen K/Z"
            value={summary.realizedPnlTry ?? 0}
            icon={CheckCircle2}
          />
        )}
      </div>
    </motion.div>
  );
}
