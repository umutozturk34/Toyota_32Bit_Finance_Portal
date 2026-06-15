import { useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { Layers, ChevronRight } from 'lucide-react';
import { containerVariants, cardVariants } from '../../../shared/utils/animations';
import { ASSET_TYPE_COLORS } from '../../../shared/constants/assetTypes';
import { useMoney } from '../../../shared/hooks/useMoney';
import Card from '../../../shared/components/card';
import Spinner from '../../../shared/components/feedback/Spinner';
import FilterTabs from '../../../shared/components/form/FilterTabs';
import RangeSelector from '../../../shared/components/form/RangeSelector';
import { positionFrame } from '../lib/positionsTableHelpers';
import { usePortfolioPositions } from '../hooks/usePortfolioData';

// Spot asset families (the fixed-income deposit/bond surface has its own breakdown).
const TYPE_OPTIONS = ['STOCK', 'CRYPTO', 'FOREX', 'COMMODITY', 'VIOP'];
const FETCH_SIZE = 500;

export default function CostBreakdownChart({ portfolioId }) {
  const { t } = useTranslation();
  const { format: money, convert, resolveTarget, currency: displayCurrency } = useMoney();
  const [status, setStatus] = useState('all');
  const [typeFilter, setTypeFilter] = useState('ALL');

  // 'all' omits the closed flag entirely (open + closed lots), matching the positions table's status convention;
  // 'open'/'closed' pin it. So the cost rollup can show total capital ever committed, not just the live book.
  const queryParams = useMemo(() => ({
    size: FETCH_SIZE,
    ...(status === 'closed' && { closed: true }),
    ...(status === 'open' && { closed: false }),
    ...(typeFilter !== 'ALL' && { assetType: typeFilter }),
  }), [status, typeFilter]);

  const { data, isLoading } = usePortfolioPositions(portfolioId, queryParams);
  const rows = useMemo(() => (Array.isArray(data) ? data : data?.content ?? []), [data]);
  const targetCurrency = displayCurrency === 'ORIGINAL' ? 'TRY' : displayCurrency;

  // In "Tümü" the breakdown rolls up by ASSET TYPE (one bar per Hisse/Kripto/…); picking a type drills into the
  // individual assets under it. Each lot's cost is the SAME per-date-FX entry cost the positions row uses (via the
  // shared positionFrame helper — cost@entry-date FX), so this card reconciles to the cent with the row + P&L card.
  const groupByType = typeFilter === 'ALL';
  const items = useMemo(() => {
    const map = new Map();
    rows.forEach((p) => {
      const key = groupByType ? p?.assetType : p?.assetCode;
      if (!key) return;
      const { isNonTryFrame, costFrame, entryValueTry } = positionFrame(p, { convert, resolveTarget });
      const cost = isNonTryFrame && costFrame != null ? costFrame : entryValueTry;
      if (!Number.isFinite(cost) || cost <= 0) return;
      const prev = map.get(key)
        || { key, type: p.assetType, code: groupByType ? null : p.assetCode,
             name: groupByType ? null : (p.assetName || p.assetCode), cost: 0, lots: 0, codes: new Set() };
      prev.cost += cost;
      prev.lots += 1;
      if (p.assetCode) prev.codes.add(p.assetCode);
      map.set(key, prev);
    });
    return [...map.values()].sort((a, b) => b.cost - a.cost);
  }, [rows, convert, resolveTarget, groupByType]);

  const total = useMemo(() => items.reduce((s, a) => s + a.cost, 0), [items]);
  const max = items.length > 0 ? items[0].cost : 0;

  const typeItems = TYPE_OPTIONS.map((type) => ({ type, label: t(`assets.labels.${type}`, { defaultValue: type }) }));

  return (
    <motion.div variants={cardVariants} initial="hidden" animate="show">
      <Card variant="elevated" radius="2xl" padding="none" backdropBlur className="group relative overflow-hidden">
        <div
          aria-hidden
          className="pointer-events-none absolute -top-20 -left-16 h-48 w-48 rounded-full bg-accent/12 blur-[80px] opacity-50 transition-opacity duration-500 group-hover:opacity-80"
        />
        <div className="relative p-4 sm:p-5 space-y-4">
          {/* Header */}
          <div className="flex items-start justify-between gap-3 flex-wrap">
            <div className="flex items-center gap-3 min-w-0">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-accent/10 ring-1 ring-inset ring-accent/20">
                <Layers className="h-4 w-4 text-accent" />
              </span>
              <div className="min-w-0">
                <p className="text-sm font-bold text-fg truncate">{t('portfolio.cost.title')}</p>
                <p className="text-[11px] text-fg-muted truncate">{t('portfolio.cost.subtitle')}</p>
              </div>
            </div>
            <div className="text-right shrink-0">
              <p className="text-[10px] uppercase tracking-wider text-fg-muted">{t('portfolio.cost.totalCost')}</p>
              <p className="text-lg font-bold font-mono text-fg">{money(total, targetCurrency)}</p>
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
                layoutId="cost-type"
              />
            </div>
            <RangeSelector
              value={status}
              onChange={setStatus}
              size="sm"
              layoutId="cost-status"
              options={[
                { id: 'all', label: t('portfolio.positions.statusAll') },
                { id: 'open', label: t('portfolio.positions.statusOpen') },
                { id: 'closed', label: t('portfolio.positions.statusClosed') },
              ]}
            />
          </div>

          {/* Per-asset cost bars */}
          {isLoading ? (
            <div className="flex items-center justify-center py-12">
              <Spinner size="md" tone="accent" />
            </div>
          ) : items.length === 0 ? (
            <div className="flex flex-col items-center justify-center gap-2 py-10 text-center">
              <Layers className="h-7 w-7 text-fg-subtle" />
              <p className="text-sm text-fg-muted">{t('portfolio.cost.empty')}</p>
            </div>
          ) : (
            <motion.div
              variants={containerVariants(0.04)}
              initial="hidden"
              animate="show"
              className="space-y-2.5"
            >
              {items.map((a) => {
                const pct = total > 0 ? (a.cost / total) * 100 : 0;
                const width = max > 0 ? (a.cost / max) * 100 : 0;
                const color = ASSET_TYPE_COLORS[a.type] || ASSET_TYPE_COLORS.CASH;
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
                        <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: color }} />
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
                        <span className="text-xs font-mono font-semibold text-fg">{money(a.cost, targetCurrency)}</span>
                        <span className="w-10 text-right text-[10px] font-mono text-fg-subtle">%{pct.toFixed(1)}</span>
                        {groupByType && (
                          <ChevronRight className="h-3.5 w-3.5 shrink-0 text-fg-subtle transition-colors group-hover/row:text-accent" />
                        )}
                      </div>
                    </div>
                    <div className="h-2 w-full overflow-hidden rounded-full bg-bg-base">
                      <motion.div
                        className="h-full rounded-full"
                        style={{ background: `linear-gradient(90deg, ${color}, ${color}cc)` }}
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
