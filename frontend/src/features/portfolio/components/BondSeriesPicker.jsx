import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { motion, AnimatePresence } from 'framer-motion';
import { Search, X, Landmark } from 'lucide-react';
import { bondService } from '../../bond/services/bondService';
import { BOND_TYPE_COLORS } from '../../bond/lib/bondConstants';
import { STALE } from '../../../shared/constants/query';

function formatMaturity(iso, localeTag) {
  if (!iso) return null;
  return new Date(`${iso.slice(0, 10)}T00:00:00`).toLocaleDateString(localeTag, { timeZone: 'Europe/Istanbul' });
}

export default function BondSeriesPicker({ value, onSelect, disabled }) {
  const { t } = useTranslation();
  const localeTag = t('common.localeTag');
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const containerRef = useRef(null);
  const trimmed = query.trim();

  const { data: results = [], isFetching } = useQuery({
    queryKey: ['bondPicker', trimmed],
    queryFn: () => bondService.getAllBonds({ search: trimmed, size: 8 }).then((r) => r?.content || []),
    enabled: open && trimmed.length >= 2,
    staleTime: STALE.SHORT,
    placeholderData: (prev) => prev,
  });

  const pick = (bond) => {
    onSelect(bond.seriesCode, bond);
    setQuery('');
    setOpen(false);
  };

  const clearSelection = () => {
    onSelect('', null);
    setQuery('');
  };

  return (
    <div ref={containerRef} className="relative">
      {value ? (
        <div className="flex items-center justify-between gap-2 rounded-lg border border-accent/30 bg-accent/5 px-3 py-2.5">
          <div className="flex items-center gap-2.5 min-w-0">
            <span className="flex items-center justify-center w-8 h-8 rounded-lg bg-accent/10 text-accent shrink-0">
              <Landmark className="h-4 w-4" />
            </span>
            <span className="text-sm font-semibold text-fg font-mono truncate">{value}</span>
          </div>
          {!disabled && (
            <button
              type="button"
              onClick={clearSelection}
              aria-label={t('portfolio.bonds.picker.clear')}
              className="flex items-center justify-center w-7 h-7 rounded-md text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer shrink-0"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          )}
        </div>
      ) : (
        <>
          <span className="absolute inset-y-0 left-3 z-10 flex items-center pointer-events-none">
            <Search className="h-3.5 w-3.5 text-fg-muted" />
          </span>
          <input
            type="text"
            value={query}
            disabled={disabled}
            onChange={(e) => { setQuery(e.target.value); setOpen(true); }}
            onFocus={() => setOpen(true)}
            onBlur={() => setTimeout(() => setOpen(false), 150)}
            placeholder={t('portfolio.bonds.picker.placeholder')}
            className="w-full rounded-lg border border-border-default bg-bg-base pl-9 pr-3 py-2.5 text-sm text-fg font-mono placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
          />
          <AnimatePresence>
            {open && trimmed.length >= 2 && (
              <motion.div
                initial={{ opacity: 0, y: -4, scale: 0.98 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: -4, scale: 0.98 }}
                transition={{ duration: 0.15 }}
                style={{ background: 'var(--color-bg-deep)', backdropFilter: 'blur(20px)', WebkitBackdropFilter: 'blur(20px)' }}
                className="absolute z-50 left-0 right-0 mt-1.5 rounded-xl border border-border-default shadow-xl overflow-hidden"
              >
                <div className="overflow-y-auto max-h-[260px]">
                  {results.length > 0 ? (
                    results.map((bond) => {
                      const typeColor = BOND_TYPE_COLORS[bond.bondType] || 'bg-accent/10 text-accent border-accent/20';
                      return (
                        <button
                          key={bond.seriesCode}
                          type="button"
                          onMouseDown={() => pick(bond)}
                          className="w-full flex items-center gap-2.5 px-3 py-2.5 text-left transition-colors cursor-pointer border-none bg-transparent hover:bg-surface/50"
                        >
                          <span className="flex items-center justify-center w-7 h-7 rounded-lg bg-accent/10 text-accent shrink-0">
                            <Landmark className="h-3.5 w-3.5" />
                          </span>
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-1.5 flex-wrap">
                              <span className="text-xs font-semibold text-fg font-mono truncate">{bond.isinCode || bond.seriesCode}</span>
                              {bond.bondType && (
                                <span className={`shrink-0 rounded border px-1 py-px text-[8px] font-bold uppercase tracking-wider ${typeColor}`}>
                                  {t(`market.bond.types.${bond.bondType}`, { defaultValue: bond.bondType })}
                                </span>
                              )}
                            </div>
                            <span className="block text-[11px] text-fg-muted truncate">{bond.seriesCode}</span>
                            {(bond.couponRate != null || bond.maturityEnd) && (
                              <span className="block text-[10px] text-fg-subtle truncate">
                                {bond.couponRate != null && t('portfolio.bonds.picker.couponShort', { rate: Number(bond.couponRate).toFixed(2) })}
                                {bond.couponRate != null && bond.maturityEnd && ' · '}
                                {bond.maturityEnd && t('portfolio.bonds.picker.maturityShort', { date: formatMaturity(bond.maturityEnd, localeTag) })}
                              </span>
                            )}
                          </div>
                        </button>
                      );
                    })
                  ) : (
                    <div className="px-3 py-4 text-center text-xs text-fg-muted">
                      {isFetching ? t('portfolio.bonds.picker.searching') : t('portfolio.bonds.picker.noResults')}
                    </div>
                  )}
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </>
      )}
    </div>
  );
}
