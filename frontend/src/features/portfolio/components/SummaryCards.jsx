import useSessionState from '../../../shared/hooks/useSessionState';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { containerVariants, cardVariants } from '../../../shared/utils/animations';
import { Wallet, BarChart3 } from 'lucide-react';
import { TrendingUp, TrendingDown } from '../../../shared/components/feedback/AnimatedIcons';
import { formatPercent, changeColors, changeBg, getChangeClass } from '../../../shared/utils/formatters';
import { useMoney } from '../../../shared/hooks/useMoney';
import { usePortfolioSummary } from '../hooks/usePortfolioData';
import { ASSET_TYPE_FILTERS as SUMMARY_FILTERS } from '../../../shared/constants/assetTypes';
import Card from '../../../shared/components/card';

function pickFrame(summary, displayCurrency) {
  if (!summary?.frames) return null;
  if (displayCurrency === 'TRY' || displayCurrency === 'ORIGINAL') return summary.frames.TRY;
  return summary.frames[displayCurrency] ?? summary.frames.TRY;
}

const VALUE_CARD_DEFS = [
  { key: 'totalValueTry', labelKey: 'portfolio.summary.marketValue', Icon: Wallet, iconBg: 'bg-accent/10', iconColor: 'text-accent', border: 'border-t-accent' },
  { key: 'totalEntryValueTry', labelKey: 'portfolio.summary.totalCost', Icon: BarChart3, iconBg: 'bg-fg-muted/10', iconColor: 'text-fg-muted', border: 'border-t-fg-muted' },
];

function PnlCard({ label, value, percent, realValue, realPercent, hideReal, base = 'TRY' }) {
  const { format: money, formatCompact: moneyCompact } = useMoney();
  const bigMoney = (v) => moneyCompact(v, base, 100_000);
  const cls = getChangeClass(value);
  const Icon = value >= 0 ? TrendingUp : TrendingDown;
  const diff = realPercent != null && percent != null
    ? Math.abs(Number(realPercent) - Number(percent))
    : null;
  const hasReal = !hideReal && realValue != null && realPercent != null && diff != null && diff >= 1;
  const realCls = hasReal ? getChangeClass(realValue) : null;
  return (
    <Card
      as={motion.div}
      variants={cardVariants}
      variant="elevated"
      accentBar={value >= 0 ? 'success' : 'danger'}
      radius="xl"
      padding="sm"
      interactive
      className="space-y-1.5"
    >
      <div className="flex items-center gap-2">
        <div className={`flex items-center justify-center w-6 h-6 rounded-md ${value >= 0 ? 'bg-success/10' : 'bg-danger/10'}`}>
          <Icon className={`h-3.5 w-3.5 ${value >= 0 ? 'text-success' : 'text-danger'}`} />
        </div>
        <span className="text-[11px] text-fg-muted font-medium">{label}</span>
      </div>
      <div className="flex items-baseline justify-between gap-2">
        <p className={`text-base font-semibold font-mono ${changeColors[cls]} truncate`} title={money(value, base)}>
          {bigMoney(value)}
        </p>
        <span className={`shrink-0 inline-flex items-center rounded px-1.5 py-0.5 text-[10px] font-medium font-mono ${changeBg[cls]} ${changeColors[cls]}`}>
          {formatPercent(percent)}
        </span>
      </div>
      {hasReal && (
        <div className="flex items-baseline justify-between gap-2 pt-1 border-t border-border-default/40">
          <span className="text-[10px] font-mono uppercase tracking-[0.12em] text-fg-subtle">reel</span>
          <div className="flex items-baseline gap-1.5">
            <span className={`text-xs font-mono tabular-nums ${changeColors[realCls]} truncate`} title={money(realValue, base)}>
              {bigMoney(realValue)}
            </span>
            <span className={`shrink-0 text-[10px] font-mono font-semibold tabular-nums ${changeColors[realCls]}`}>
              {formatPercent(realPercent)}
            </span>
          </div>
        </div>
      )}
    </Card>
  );
}

export default function SummaryCards({ summary: initialSummary, portfolioId }) {
  const { t } = useTranslation();
  const { format: money, formatCompact: moneyCompact, currency: displayCurrency } = useMoney();
  const bigMoney = (v) => moneyCompact(v, 'TRY', 100_000);
  const [activeFilter, setActiveFilter] = useSessionState('portfolio-summary-filter', null);

  const { data: filteredSummary, isFetching: loading } = usePortfolioSummary(portfolioId, activeFilter);
  const summary = activeFilter ? (filteredSummary ?? initialSummary) : initialSummary;
  const filterLabel = (id) => id ? t(`assets.labels.${id}`) : t('assets.labels.ALL');

  const isNonTryFrame = displayCurrency === 'USD' || displayCurrency === 'EUR';
  const frame = pickFrame(summary, displayCurrency);
  const totalPnlPercent = frame?.pnlPercent ?? summary?.pnlPercent;
  const dailyPnlPercent = frame?.dailyPnlPercent ?? summary?.dailyPnlPercent;
  const totalPnlAmount = isNonTryFrame && frame?.totalPnl != null
    ? { value: frame.totalPnl, base: displayCurrency }
    : { value: summary?.totalPnlTry, base: 'TRY' };
  const dailyPnlAmount = isNonTryFrame && frame?.dailyPnl != null
    ? { value: frame.dailyPnl, base: displayCurrency }
    : { value: summary?.dailyPnlTry, base: 'TRY' };
  const totalValueAmount = isNonTryFrame && frame?.totalValue != null
    ? { value: frame.totalValue, base: displayCurrency }
    : { value: summary?.totalValueTry, base: 'TRY' };
  const totalEntryAmount = isNonTryFrame && frame?.totalEntry != null
    ? { value: frame.totalEntry, base: displayCurrency }
    : { value: summary?.totalEntryValueTry, base: 'TRY' };

  return (
    <motion.div
      variants={containerVariants(0.06)}
      initial="hidden"
      animate="show"
      className="space-y-3"
    >
      <div className="flex items-center justify-between gap-2 flex-wrap">
        <div className="flex gap-0.5 rounded-lg border border-border-default bg-bg-elevated p-0.5 overflow-x-auto max-w-full">
          {SUMMARY_FILTERS.map(({ id }) => (
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
                {filterLabel(id)}
              </span>
            </button>
          ))}
        </div>
        {loading && (
          <span className="text-[10px] text-fg-muted animate-pulse">{t('portfolio.summary.refreshing')}</span>
        )}
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {VALUE_CARD_DEFS.map(({ key, labelKey, Icon, iconBg, iconColor, border }) => {
          const amount = key === 'totalValueTry' ? totalValueAmount : totalEntryAmount;
          const bigInBase = (v) => moneyCompact(v, amount.base, 100_000);
          return (
            <Card
              as={motion.div}
              key={key}
              variants={cardVariants}
              variant="elevated"
              radius="xl"
              padding="sm"
              interactive
              className={`space-y-1.5 border-t-2 ${border}`}
            >
              <div className="flex items-center gap-2">
                <div className={`flex items-center justify-center w-6 h-6 rounded-md ${iconBg}`}>
                  <Icon className={`h-3.5 w-3.5 ${iconColor}`} />
                </div>
                <span className="text-[11px] text-fg-muted font-medium">{t(labelKey)}</span>
              </div>
              <p className="text-base font-semibold font-mono text-fg truncate" title={money(amount.value, amount.base)}>
                {bigInBase(amount.value)}
              </p>
            </Card>
          );
        })}
        <PnlCard
          label={t('portfolio.summary.profitLoss')}
          value={totalPnlAmount.value}
          base={totalPnlAmount.base}
          percent={totalPnlPercent}
          realValue={summary?.realPnlTry}
          realPercent={summary?.realPnlPercent}
          hideReal={isNonTryFrame}
        />
        <PnlCard
          label={t('portfolio.summary.dailyPnl')}
          value={dailyPnlAmount.value}
          base={dailyPnlAmount.base}
          percent={dailyPnlPercent}
        />
      </div>
    </motion.div>
  );
}
