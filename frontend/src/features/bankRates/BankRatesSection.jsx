import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { motion, AnimatePresence } from 'framer-motion';
import { Banknote, ChevronDown, Search, X, ArrowUp, ArrowDown } from 'lucide-react';
import { useBankRates } from './hooks/useBankRates';
import { currentLocaleTag } from '../../shared/utils/formatters';

const fmt = (v) => (v == null ? '—' : Number(v).toLocaleString(currentLocaleTag(), { maximumFractionDigits: 4 }));
const SORTS = [
  { id: 'buy', labelKey: 'bankRates.sortBuy' },
  { id: 'sell', labelKey: 'bankRates.sortSell' },
  { id: 'spread', labelKey: 'bankRates.sortSpread' },
];
// First click: highest buy, cheapest sell, tightest spread.
const DEFAULT_DIR = { buy: 'desc', sell: 'asc', spread: 'asc' };

// Collapsible bank-rates block for a forex/commodity detail page. Shows per-bank buy/sell with a bank-name
// search and sort by buy / sell / spread (makas), mirroring the full panel. Renders nothing without rates.
export default function BankRatesSection({ currency, kind = 'CURRENCY' }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [sortBy, setSortBy] = useState('');
  const [sortDir, setSortDir] = useState('desc');
  const { data } = useBankRates({ currency, kind });
  const rates = useMemo(() => (Array.isArray(data) ? data : []), [data]);

  const toggleSort = (id) => {
    if (sortBy === id) setSortDir((d) => (d === 'desc' ? 'asc' : 'desc'));
    else { setSortBy(id); setSortDir(DEFAULT_DIR[id]); }
  };

  const rows = useMemo(() => {
    const q = query.trim().toLocaleLowerCase('tr-TR');
    let r = q ? rates.filter((x) => (x.bankName || '').toLocaleLowerCase('tr-TR').includes(q)) : rates;
    if (sortBy) {
      r = [...r].sort((a, b) => {
        const av = sortBy === 'spread' ? Number(a.sellRate) - Number(a.buyRate) : Number(a[`${sortBy}Rate`]);
        const bv = sortBy === 'spread' ? Number(b.sellRate) - Number(b.buyRate) : Number(b[`${sortBy}Rate`]);
        if (Number.isNaN(av)) return 1;
        if (Number.isNaN(bv)) return -1;
        return sortDir === 'asc' ? av - bv : bv - av;
      });
    }
    return r;
  }, [rates, query, sortBy, sortDir]);

  if (!currency || rates.length === 0) return null;

  return (
    <div className="overflow-hidden rounded-2xl border border-border-default bg-bg-elevated/50 backdrop-blur">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex w-full items-center gap-2.5 px-4 py-3 text-left transition-colors hover:bg-surface/40 cursor-pointer"
      >
        <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-emerald-500/15 text-emerald-400">
          <Banknote className="h-4 w-4" />
        </span>
        <span className="text-sm font-bold uppercase tracking-wider text-fg">{t('bankRates.sectionTitle')}</span>
        <span className="rounded-full bg-surface/70 px-2 py-0.5 text-[10px] font-bold tabular-nums text-fg-muted">
          {t('bankRates.bankCount', { count: rates.length })}
        </span>
        <ChevronDown className={`ml-auto h-4 w-4 shrink-0 text-fg-subtle transition-transform duration-200 ${open ? 'rotate-180' : ''}`} />
      </button>
      <AnimatePresence initial={false}>
        {open && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.22, ease: [0.32, 0.72, 0, 1] }}
            className="overflow-hidden"
          >
            <div className="space-y-3 border-t border-border-default/60 p-3">
              <div className="flex flex-wrap items-center gap-2">
                <div className="relative min-w-0 flex-1 sm:max-w-56">
                  <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-fg-subtle" />
                  <input
                    type="text"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    maxLength={40}
                    placeholder={t('bankRates.searchBank')}
                    className="w-full rounded-lg border border-border-default bg-bg-base/60 py-1.5 pl-8 pr-7 text-[11px] font-medium text-fg placeholder:text-fg-subtle focus:border-accent/50 focus:outline-none"
                  />
                  {query && (
                    <button type="button" onClick={() => setQuery('')} className="absolute right-1.5 top-1/2 -translate-y-1/2 p-0.5 text-fg-subtle hover:text-fg">
                      <X className="h-3 w-3" />
                    </button>
                  )}
                </div>
                <div className="flex items-center gap-1">
                  <span className="text-[10px] font-bold uppercase tracking-wider text-fg-subtle">{t('bankRates.sort')}</span>
                  {SORTS.map((s) => {
                    const active = sortBy === s.id;
                    return (
                      <button
                        key={s.id}
                        type="button"
                        onClick={() => toggleSort(s.id)}
                        className={`inline-flex items-center gap-0.5 rounded-md px-2 py-1 text-[10px] font-bold transition-colors ${
                          active ? 'bg-accent/15 text-accent-bright' : 'text-fg-muted hover:bg-surface/60 hover:text-fg'
                        }`}
                      >
                        {t(s.labelKey)}
                        {active && (sortDir === 'asc' ? <ArrowUp className="h-2.5 w-2.5" /> : <ArrowDown className="h-2.5 w-2.5" />)}
                      </button>
                    );
                  })}
                </div>
              </div>
              <div className="grid grid-cols-1 gap-1.5 sm:grid-cols-2 lg:grid-cols-3">
                {rows.map((r) => {
                  const isMarket = r.bankCode === 'MARKET';
                  const name = isMarket ? t('bankRates.marketBank') : r.bankName;
                  const spread = r.buyRate != null && r.sellRate != null ? Number(r.sellRate) - Number(r.buyRate) : null;
                  return (
                    <div key={`${r.source}:${r.bankCode}`} className="flex items-center gap-2.5 rounded-lg border border-border-default bg-bg-base/40 px-3 py-2">
                      {r.bankLogoUrl
                        ? <img src={r.bankLogoUrl} alt="" className="h-6 w-6 shrink-0 rounded object-contain bg-white/90" onError={(e) => { e.target.style.display = 'none'; }} />
                        : <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded bg-surface text-[9px] font-bold text-fg-muted">{(r.bankCode || '?').slice(0, 3).toUpperCase()}</span>}
                      <div className="min-w-0 flex-1">
                        <span className="block truncate text-[12px] font-semibold text-fg">{name}</span>
                        {spread != null && <span className="block text-[9px] uppercase tracking-wider text-fg-subtle">{t('bankRates.spread')} {fmt(spread)}</span>}
                      </div>
                      <span className="shrink-0 text-right">
                        <span className="block text-[9px] uppercase tracking-wider text-fg-subtle">{t('bankRates.buy')}</span>
                        <span className="block font-mono text-[11px] font-bold text-success">{fmt(r.buyRate)}</span>
                      </span>
                      <span className="shrink-0 text-right">
                        <span className="block text-[9px] uppercase tracking-wider text-fg-subtle">{t('bankRates.sell')}</span>
                        <span className="block font-mono text-[11px] font-bold text-danger">{fmt(r.sellRate)}</span>
                      </span>
                    </div>
                  );
                })}
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
