import { useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { TrendingUp, TrendingDown, ChevronRight } from 'lucide-react';
import { containerVariants, cardVariants } from '../../../shared/utils/animations';
import { ASSET_TYPE_COLORS } from '../../../shared/constants/assetTypes';
import { useMoney } from '../../../shared/hooks/useMoney';
import { visibleDecimals } from '../../../shared/utils/formatters';
import Card from '../../../shared/components/card';
import { SkeletonChart } from '../../../shared/components/feedback/Skeleton';
import EmptyState from '../../../shared/components/feedback/EmptyState';
import FilterTabs from '../../../shared/components/form/FilterTabs';
import RangeSelector from '../../../shared/components/form/RangeSelector';
import { positionFrame } from '../lib/positionsTableHelpers';
import { usePortfolioPositions } from '../hooks/usePortfolioData';

// The overview companion to CostBreakdownChart: instead of "where the money went" it shows "where the gain/loss
// came from", per asset type, with the same rollup + drill-in. P&L is SIGNED (a type can be a net drag), so rows
// are coloured by sign and the bar scales to the LARGEST absolute contribution, not the net total (which can sit
// near zero while individual types swing hard in opposite directions). The P&L-over-TIME view lives on the K/Z tab.
const TYPE_OPTIONS = ['STOCK', 'CRYPTO', 'FOREX', 'COMMODITY', 'VIOP'];
const FETCH_SIZE = 500;

export default function PnlByTypeChart({ portfolioId }) {
  const { t } = useTranslation();
  const { format: money, convert, resolveTarget, currency: displayCurrency } = useMoney();
  const [status, setStatus] = useState('all');
  const [typeFilter, setTypeFilter] = useState('ALL');

  // 'all' omits the closed flag (open + closed lots) so the breakdown shows TOTAL P&L — realized (closed) plus
  // unrealized (open) — per type; 'open'/'closed' pin it. The per-lot branch below values each lot correctly
  // regardless (realized@exit-FX for closed, value−cost for open), so mixing them in 'all' simply sums to total.
  const queryParams = useMemo(() => ({
    size: FETCH_SIZE,
    ...(status === 'closed' && { closed: true }),
    ...(status === 'open' && { closed: false }),
    ...(typeFilter !== 'ALL' && { assetType: typeFilter }),
  }), [status, typeFilter]);

  const { data, isLoading } = usePortfolioPositions(portfolioId, queryParams);
  const rows = useMemo(() => (Array.isArray(data) ? data : data?.content ?? []), [data]);
  const targetCurrency = displayCurrency === 'ORIGINAL' ? 'TRY' : displayCurrency;

  // Cost & P&L come from the ONE canonical positionFrame() — the exact same per-date-FX computation the positions
  // row uses (value@today/exit − cost@entry, each at its own date's FX; VIOP SHORT direction-aware) — so this
  // breakdown and the row can never disagree. In the TRY frame the helper defers to the backend pnlTry verbatim.
  const groupByType = typeFilter === 'ALL';
  const items = useMemo(() => {
    const map = new Map();
    rows.forEach((p) => {
      const key = groupByType ? p?.assetType : p?.assetCode;
      if (!key) return;
      const { isNonTryFrame, costFrame, framePnl, entryValueTry } = positionFrame(p, { convert, resolveTarget });
      // ORIGINAL rolls up in TRY: a cross-type aggregate can't sum mixed native currencies (USD crypto + TRY stock),
      // so only a concrete display currency (USD/EUR) uses the per-date native frame; ORIGINAL (and TRY) defer to the
      // backend TRY figures — matching targetCurrency above and the allocation card.
      const useFrame = displayCurrency !== 'ORIGINAL' && isNonTryFrame && framePnl != null;
      const cost = useFrame ? costFrame : entryValueTry;
      const pnl = useFrame ? framePnl : Number(p.pnlTry);
      if (!Number.isFinite(cost) || cost <= 0 || !Number.isFinite(pnl)) return;
      const prev = map.get(key)
        || { key, type: p.assetType, code: groupByType ? null : p.assetCode,
             name: groupByType ? null : (p.assetName || p.assetCode), pnl: 0, cost: 0, lots: 0, codes: new Set() };
      prev.pnl += pnl;
      prev.cost += cost;
      prev.lots += 1;
      if (p.assetCode) prev.codes.add(p.assetCode);
      map.set(key, prev);
    });
    return [...map.values()].sort((a, b) => Math.abs(b.pnl) - Math.abs(a.pnl));
  }, [rows, convert, resolveTarget, groupByType, displayCurrency]);

  const totalPnl = useMemo(() => items.reduce((s, a) => s + a.pnl, 0), [items]);
  const totalCost = useMemo(() => items.reduce((s, a) => s + a.cost, 0), [items]);
  const overallReturn = totalCost > 0 ? (totalPnl / totalCost) * 100 : null;
  const maxAbs = items.reduce((m, a) => Math.max(m, Math.abs(a.pnl)), 0);
  const totalUp = totalPnl >= 0;

  const typeItems = TYPE_OPTIONS.map((type) => ({ type, label: t(`assets.labels.${type}`, { defaultValue: type }) }));

  return (
    <motion.div variants={cardVariants} initial="hidden" animate="show">
      <Card variant="elevated" radius="2xl" padding="none" backdropBlur className="group relative overflow-hidden">
        <div
          aria-hidden
          className={`pointer-events-none absolute -top-20 -left-16 h-48 w-48 rounded-full blur-[80px] opacity-50 transition-opacity duration-500 group-hover:opacity-80 ${totalUp ? 'bg-success/12' : 'bg-danger/12'}`}
        />
        <div className="relative p-4 sm:p-5 space-y-4">
          {/* Header */}
          <div className="flex items-start justify-between gap-3 flex-wrap">
            <div className="flex items-center gap-3 min-w-0">
              <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ring-1 ring-inset ${totalUp ? 'bg-success/10 ring-success/20' : 'bg-danger/10 ring-danger/20'}`}>
                {totalUp ? <TrendingUp className="h-4 w-4 text-success" /> : <TrendingDown className="h-4 w-4 text-danger" />}
              </span>
              <div className="min-w-0">
                <p className="text-sm font-bold text-fg truncate">{t('portfolio.pnlByType.title')}</p>
                <p className="text-[11px] text-fg-muted truncate">{t('portfolio.pnlByType.subtitle')}</p>
              </div>
            </div>
            <div className="text-right shrink-0">
              <p className="text-[10px] uppercase tracking-wider text-fg-muted">{t('portfolio.pnlByType.total')}</p>
              <p className={`text-lg font-bold font-mono ${totalUp ? 'text-success' : 'text-danger'}`}>
                {totalUp ? '+' : ''}{money(totalPnl, targetCurrency)}
                {overallReturn != null && (
                  <span className="ml-1.5 text-xs font-semibold">({totalUp ? '+' : ''}%{overallReturn.toFixed(visibleDecimals(overallReturn, 1))})</span>
                )}
              </p>
            </div>
          </div>

          {/* Filters: open/closed + asset type */}
          <div className="flex items-center justify-between gap-2 flex-wrap">
            <div className="max-w-full overflow-x-auto">
              <FilterTabs
                items={typeItems}
                activeId={typeFilter}
                onSelect={setTypeFilter}
                allLabel={t('portfolio.positions.statusAll')}
                showAll
                layoutId="pnltype-type"
              />
            </div>
            <RangeSelector
              value={status}
              onChange={setStatus}
              size="sm"
              layoutId="pnltype-status"
              options={[
                { id: 'all', label: t('portfolio.positions.statusAll') },
                { id: 'open', label: t('portfolio.positions.statusOpen') },
                { id: 'closed', label: t('portfolio.positions.statusClosed') },
              ]}
            />
          </div>

          {/* Per-type / per-asset P&L bars */}
          {isLoading ? (
            <SkeletonChart h="13rem" />
          ) : items.length === 0 ? (
            <EmptyState size="sm" variant="empty" message={t('portfolio.pnlByType.empty')} />
          ) : (
            <motion.div
              variants={containerVariants(0.04)}
              initial="hidden"
              animate="show"
              className="space-y-2.5"
            >
              {items.map((a) => {
                const up = a.pnl >= 0;
                const ret = a.cost > 0 ? (a.pnl / a.cost) * 100 : null;
                const width = maxAbs > 0 ? (Math.abs(a.pnl) / maxAbs) * 100 : 0;
                const dotColor = ASSET_TYPE_COLORS[a.type] || ASSET_TYPE_COLORS.CASH;
                const signColor = up ? 'var(--color-success)' : 'var(--color-danger)';
                const typeLabel = t(`assets.labels.${a.type}`, { defaultValue: a.type });
                // In the "Tümü" rollup each bar is a TYPE and clicking it drills into that type's assets; once a
                // type is selected each bar is an individual asset (no further drill).
                const RowTag = groupByType ? motion.button : motion.div;
                return (
                  <RowTag
                    key={a.key}
                    variants={cardVariants}
                    {...(groupByType
                      ? { type: 'button', onClick: () => setTypeFilter(a.type),
                          'aria-label': t('portfolio.cost.drillInto', { type: typeLabel }) }
                      : {})}
                    className={`group/row block w-full space-y-1 border-none bg-transparent p-0 text-left ${
                      groupByType ? 'cursor-pointer' : ''}`}
                  >
                    <div className="flex items-center justify-between gap-3">
                      <div className="flex items-center gap-2 min-w-0">
                        <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: dotColor }} />
                        <span className="text-xs font-mono font-semibold text-fg truncate transition-colors group-hover/row:text-accent">
                          {groupByType ? typeLabel : a.code}
                        </span>
                        {!groupByType && a.name && a.name !== a.code && (
                          <span className="hidden truncate text-[11px] text-fg-muted sm:inline">{a.name}</span>
                        )}
                        {groupByType ? (
                          <span className="shrink-0 text-[10px] font-mono text-fg-subtle">
                            {t('portfolio.cost.assets', { count: a.codes.size })}
                          </span>
                        ) : (
                          a.lots > 1 && (
                            <span className="shrink-0 text-[10px] font-mono text-fg-subtle">{t('portfolio.cost.lots', { count: a.lots })}</span>
                          )
                        )}
                      </div>
                      <div className="flex items-center gap-2 shrink-0">
                        <span className={`text-xs font-mono font-semibold ${up ? 'text-success' : 'text-danger'}`}>
                          {up ? '+' : ''}{money(a.pnl, targetCurrency)}
                        </span>
                        {ret != null && (
                          <span className={`w-12 text-right text-[10px] font-mono ${up ? 'text-success/80' : 'text-danger/80'}`}>
                            {up ? '+' : ''}%{ret.toFixed(visibleDecimals(ret, 1))}
                          </span>
                        )}
                        {groupByType && (
                          <ChevronRight className="h-3.5 w-3.5 shrink-0 text-fg-subtle transition-colors group-hover/row:text-accent" />
                        )}
                      </div>
                    </div>
                    <div className="h-2 w-full overflow-hidden rounded-full bg-bg-base">
                      <motion.div
                        className="h-full rounded-full"
                        style={{ background: `linear-gradient(90deg, ${signColor}, color-mix(in srgb, ${signColor} 75%, transparent))` }}
                        initial={{ width: 0 }}
                        animate={{ width: `${width}%` }}
                        transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
                      />
                    </div>
                  </RowTag>
                );
              })}
            </motion.div>
          )}
        </div>
      </Card>
    </motion.div>
  );
}
