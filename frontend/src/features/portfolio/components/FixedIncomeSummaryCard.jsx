import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import ReactECharts from 'echarts-for-react';
import { Landmark, FileText, BarChart3, CalendarClock } from 'lucide-react';
import { TrendingUp, TrendingDown } from '../../../shared/components/feedback/AnimatedIcons';
import { containerVariants, cardVariants } from '../../../shared/utils/animations';
import { formatPercentSmart, changeColors, changeBg, getChangeClass } from '../../../shared/utils/formatters';
import Card from '../../../shared/components/card';
import Spinner from '../../../shared/components/feedback/Spinner';
import FitMoney from '../../../shared/components/FitMoney';
import { useTheme } from '../../../shared/context/useTheme';
import { useFixedIncomeSummary } from '../hooks/useFixedIncomePositions';

const DEPOSIT_COLOR = '#6366f1';
const BOND_COLOR = '#10b981';

/** One side of the allocation legend: a colour dot, icon, label, value and share. */
function LegendRow({ tone, Icon, label, value, share }) {
  const dot = tone === 'accent' ? 'bg-accent' : 'bg-success';
  const txt = tone === 'accent' ? 'text-accent' : 'text-success';
  return (
    <div className="flex items-center gap-2 min-w-0">
      <span className={`h-2 w-2 shrink-0 rounded-full ${dot}`} />
      <Icon className={`h-3.5 w-3.5 shrink-0 ${txt}`} />
      <span className="text-[11px] text-fg-muted truncate">{label}</span>
      <span className="ml-auto flex items-baseline gap-1.5 shrink-0">
        <FitMoney value={value} base="TRY" pinned className="text-xs font-mono font-semibold text-fg" />
        <span className={`text-[10px] font-mono ${txt}`}>{formatPercentSmart(share)}</span>
      </span>
    </div>
  );
}

export default function FixedIncomeSummaryCard({ portfolioId }) {
  const { t, i18n } = useTranslation();
  const { isDark } = useTheme();
  const { data: summary, isLoading } = useFixedIncomeSummary(portfolioId);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Spinner size="md" tone="accent" />
      </div>
    );
  }

  const depositCount = summary?.depositCount ?? 0;
  const bondCount = summary?.bondCount ?? 0;

  if (depositCount === 0 && bondCount === 0) {
    return (
      <p className="text-sm text-fg-muted text-center py-8">
        {t('portfolio.fixedIncome.empty')}
      </p>
    );
  }

  const value = summary?.totalValueTry ?? 0;
  const couponsReceived = summary?.bondCouponsReceivedTry ?? 0;
  // The holder's true total = clean mark-to-market value PLUS the coupon cash already received (realized income);
  // K/Z folds that income in too, so the headline isn't a price-only figure. Percent is recomputed on the
  // coupon-inclusive K/Z (the backend pnlPercent is price-only).
  const total = value + couponsReceived;
  const cost = summary?.totalCostTry ?? 0;
  const pnl = (summary?.totalPnlTry ?? 0) + couponsReceived;
  const pnlPercent = cost > 0 ? (pnl / cost) * 100 : null;
  const pnlCls = getChangeClass(pnl);
  const PnlIcon = pnl >= 0 ? TrendingUp : TrendingDown;
  const depositValue = summary?.depositValueTry ?? 0;
  const bondValue = summary?.bondValueTry ?? 0;
  const splitTotal = depositValue + bondValue;
  const depositShare = splitTotal > 0 ? (depositValue / splitTotal) * 100 : 0;
  const bondShare = splitTotal > 0 ? 100 - depositShare : 0;
  const asOfLabel = summary?.asOf
    ? new Date(summary.asOf).toLocaleDateString(i18n.language || 'tr', {
      day: '2-digit', month: 'short', year: 'numeric',
    })
    : null;

  // Plain ECharts donut (matches the spot AllocationChart) — no glow/gradient. Center shows the holding count.
  const ringStroke = isDark ? '#0d1117' : '#ffffff';
  const labelFg = isDark ? '#e6edf3' : '#1b1f24';
  const labelMuted = isDark ? '#7d8590' : '#94a3b8';
  const allocationOption = {
    backgroundColor: 'transparent',
    tooltip: { show: false },
    series: [{
      type: 'pie',
      radius: ['62%', '90%'],
      center: ['50%', '50%'],
      avoidLabelOverlap: false,
      silent: true,
      itemStyle: { borderColor: ringStroke, borderWidth: 2 },
      label: {
        show: true,
        position: 'center',
        formatter: () => `{count|${depositCount + bondCount}}\n{lbl|${t('portfolio.fixedIncome.allocation')}}`,
        rich: {
          count: { fontSize: 18, fontWeight: 700, color: labelFg, fontFamily: "'JetBrains Mono', monospace" },
          lbl: { fontSize: 8, color: labelMuted, padding: [3, 0, 0, 0] },
        },
      },
      labelLine: { show: false },
      data: [
        { value: depositValue, name: t('portfolio.fixedIncome.depositSplit'), itemStyle: { color: DEPOSIT_COLOR } },
        { value: bondValue, name: t('portfolio.fixedIncome.bondSplit'), itemStyle: { color: BOND_COLOR } },
      ].filter((d) => d.value > 0),
    }],
  };

  return (
    <motion.div variants={containerVariants(0.06)} initial="hidden" animate="show">
      <Card
        as={motion.div}
        variants={cardVariants}
        variant="elevated"
        radius="2xl"
        padding="none"
        backdropBlur
        className="group relative overflow-hidden"
      >
        <div
          aria-hidden
          className="pointer-events-none absolute -top-24 -left-16 h-56 w-56 rounded-full bg-accent/15 blur-[90px] opacity-60 transition-opacity duration-500 group-hover:opacity-90"
        />
        <div
          aria-hidden
          className="pointer-events-none absolute -bottom-24 -right-12 h-52 w-52 rounded-full bg-success/10 blur-[90px] opacity-50"
        />

        <div className="relative p-4 sm:p-5 space-y-5">
          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-2.5 min-w-0">
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-accent/10 ring-1 ring-inset ring-accent/20">
                <Landmark className="h-4 w-4 text-accent" />
              </span>
              <div className="min-w-0">
                <h3 className="text-sm font-semibold text-fg truncate">{t('portfolio.fixedIncome.title')}</h3>
                <p className="text-[11px] text-fg-subtle truncate">{t('portfolio.fixedIncome.subtitle')}</p>
              </div>
            </div>
            <div className="flex shrink-0 items-center gap-1.5">
              <span
                title={t('portfolio.fixedIncome.tryOnlyHint')}
                className="inline-flex items-center gap-1 rounded-md bg-bg-base/60 px-2 py-1 text-[10px] font-semibold text-fg-muted ring-1 ring-inset ring-border-default/50"
              >
                {t('portfolio.fixedIncome.tryOnly')}
              </span>
              {asOfLabel && (
                <span className="inline-flex items-center gap-1 rounded-md bg-bg-base/60 px-2 py-1 text-[10px] font-mono text-fg-muted ring-1 ring-inset ring-border-default/50">
                  <CalendarClock className="h-3 w-3" />
                  {t('portfolio.fixedIncome.asOf', { date: asOfLabel })}
                </span>
              )}
            </div>
          </div>

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-12 lg:items-center">
            <div className="lg:col-span-7 space-y-3">
              <div className="space-y-1">
                <span className="text-[11px] uppercase tracking-wider text-fg-muted font-medium">
                  {t('portfolio.fixedIncome.totalValue')}
                </span>
                <FitMoney value={total} base="TRY" pinned className="block text-3xl font-bold font-mono text-fg" />
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <span className={`inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-sm font-semibold font-mono ${changeBg[pnlCls]} ${changeColors[pnlCls]}`}>
                  <PnlIcon className="h-4 w-4" />
                  <FitMoney value={pnl} base="TRY" pinned className="font-mono" />
                </span>
                <span className={`inline-flex items-center rounded-md px-2 py-1 text-xs font-medium font-mono ${changeBg[pnlCls]} ${changeColors[pnlCls]}`}>
                  {formatPercentSmart(pnlPercent)}
                </span>
              </div>
              <div className="flex items-center gap-1.5 text-xs text-fg-muted">
                <BarChart3 className="h-3.5 w-3.5 shrink-0" />
                <span>{t('portfolio.fixedIncome.totalCost')}</span>
                <FitMoney value={cost} base="TRY" pinned className="font-mono text-fg-muted" />
              </div>
              {couponsReceived > 0 && (
                <div className="flex items-center gap-1.5 text-xs text-fg-muted">
                  <CalendarClock className="h-3.5 w-3.5 shrink-0 text-success" />
                  <span>{t('portfolio.fixedIncome.couponsReceived')}</span>
                  <FitMoney value={couponsReceived} base="TRY" pinned className="font-mono text-success" />
                </div>
              )}
            </div>

            <div className="lg:col-span-5 flex items-center gap-4">
              <div className="h-24 w-24 shrink-0">
                <ReactECharts
                  option={allocationOption}
                  notMerge
                  style={{ height: 96, width: 96 }}
                  opts={{ renderer: 'canvas' }}
                />
              </div>
              <div className="min-w-0 flex-1 space-y-2.5">
                <LegendRow
                  tone="accent"
                  Icon={Landmark}
                  label={t('portfolio.fixedIncome.depositSplit')}
                  value={depositValue}
                  share={depositShare}
                />
                <LegendRow
                  tone="success"
                  Icon={FileText}
                  label={t('portfolio.fixedIncome.bondSplit')}
                  value={bondValue}
                  share={bondShare}
                />
              </div>
            </div>
          </div>
        </div>
      </Card>
    </motion.div>
  );
}
