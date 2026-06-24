import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { ArrowDown, ArrowUp, ArrowUpDown, Banknote, Clock, RefreshCw, Search, X } from 'lucide-react';
import { useBankRates, useBankRateCurrencies } from './hooks/useBankRates';
import { flagEmoji } from './flagEmoji';
import BankCard from './components/BankCard';
import KindCurrencyControls from './components/KindCurrencyControls';
import Spinner from '../../shared/components/feedback/Spinner';
import { useMoney } from '../../shared/hooks/useMoney';
import useWheelToHorizontal from '../../shared/hooks/useWheelToHorizontal';
import { formatDateTimeShort } from '../../shared/utils/formatters';
import { commodityVisual } from '../../shared/icons/commodities';

const DEFAULT_CURRENCY_BY_KIND = { CURRENCY: 'USD', GOLD: 'GRAM_ALTIN' };

// Reverse link "bank rate → asset detail": the currencies that exist as forex assets (seed) get a /forex/<code>
// detail; the only consumer gold with a tracked commodity is gram gold. Anything else has no detail → no link.
const FOREX_DETAIL_CODES = new Set(['USD', 'EUR', 'AED', 'AUD', 'AZN', 'CNY', 'DKK', 'KRW', 'GBP', 'SEK', 'CHF', 'JPY', 'CAD', 'QAR', 'KZT', 'KWD', 'NOK', 'PKR', 'RON', 'RUB', 'SAR', 'XDR']);
const GOLD_DETAIL_ROUTE = { GRAM_ALTIN: '/commodities/XAUTRYG', GUMUS: '/commodities/XAGTRYG' };
const assetDetailRoute = (kind, code) =>
  kind === 'GOLD' ? (GOLD_DETAIL_ROUTE[code] ?? null) : (FOREX_DETAIL_CODES.has(code) ? `/forex/${code}` : null);

// Sort the bank list by a rate column; clicking an active chip flips its arrow, mirroring the returns page.
const SORT_CHIPS = [
  { id: 'buy', labelKey: 'bankRates.sortBuy' },
  { id: 'sell', labelKey: 'bankRates.sortSell' },
  { id: 'spread', labelKey: 'bankRates.sortSpread' },
];
// First-click direction per column: highest buy, cheapest sell, tightest spread.
const SORT_DEFAULT_DIR = { buy: 'desc', sell: 'asc', spread: 'asc' };

export default function BankRatesPanel() {
  const { t } = useTranslation();
  const localeTag = t('common.localeTag');
  const labelFor = useCallback((code) => t(`bankRates.currency.${code}`, code), [t]);
  const { format: money, currency: displayCurrency } = useMoney();
  const [searchParams, setSearchParams] = useSearchParams();
  const kindParam = searchParams.get('kind');
  const kind = kindParam === 'GOLD' ? 'GOLD' : 'CURRENCY';
  const currency = searchParams.get('rate') || DEFAULT_CURRENCY_BY_KIND[kind];
  const detailRoute = assetDetailRoute(kind, currency);
  const setKind = useCallback((nextKind) => {
    const next = new URLSearchParams(searchParams);
    if (nextKind === 'CURRENCY') next.delete('kind'); else next.set('kind', nextKind);
    next.set('rate', DEFAULT_CURRENCY_BY_KIND[nextKind]);
    setSearchParams(next, { replace: true });
  }, [searchParams, setSearchParams]);
  const setCurrency = useCallback((nextCurrency) => {
    const next = new URLSearchParams(searchParams);
    next.set('rate', nextCurrency);
    setSearchParams(next, { replace: true });
  }, [searchParams, setSearchParams]);
  const [currencyQuery, setCurrencyQuery] = useState('');
  const [bankQuery, setBankQuery] = useState('');
  const [sortBy, setSortBy] = useState('');
  const [sortDir, setSortDir] = useState('desc');
  const toggleSort = useCallback((id) => {
    if (sortBy === id) {
      setSortDir((d) => (d === 'desc' ? 'asc' : 'desc'));
    } else {
      setSortBy(id);
      setSortDir(SORT_DEFAULT_DIR[id]);
    }
  }, [sortBy]);

  const { data: currencies = [], isLoading: currenciesLoading } = useBankRateCurrencies({ kind });
  const { data: rates = [], isLoading: ratesLoading, isFetching, refetch } = useBankRates({ currency, kind });

  const orderedCurrencies = useMemo(() => {
    const known = [
      'USD', 'EUR', 'GBP', 'CHF', 'SAR', 'AUD', 'CAD', 'JPY',
      'CNY', 'DKK', 'SEK', 'NOK',
      'RUB', 'QAR', 'KWD', 'AED', 'ZAR', 'HKD', 'PLN', 'RON',
      'SGD', 'NZD', 'CZK', 'HUF', 'INR', 'THB', 'MXN', 'BRL',
    ];
    const goldOrder = [
      // Gram silver sits right after gram gold — the two per-gram spot values belong side by side.
      'GRAM_ALTIN', 'GUMUS', 'CEYREK_ALTIN', 'YARIM_ALTIN', 'TAM_ALTIN', 'ATA_ALTIN',
      'CUMHURIYET_ALTINI', 'HAMIT_ALTIN', 'RESAT_ALTIN', 'BESLI_ALTIN', 'GRAM_HAS_ALTIN',
      'AYAR_14', 'AYAR_18', 'AYAR_22_BILEZIK',
    ];
    const wanted = kind === 'GOLD' ? goldOrder : known;
    const set = new Set(currencies);
    return [
      ...wanted.filter((c) => set.has(c)),
      ...currencies.filter((c) => !wanted.includes(c)),
    ];
  }, [currencies, kind]);

  const filteredCurrencies = useMemo(() => {
    const q = currencyQuery.trim().toLocaleLowerCase('tr-TR');
    if (!q) return orderedCurrencies;
    return orderedCurrencies.filter((code) =>
      code.toLocaleLowerCase('tr-TR').includes(q)
      || labelFor(code).toLocaleLowerCase('tr-TR').includes(q)
    );
  }, [orderedCurrencies, currencyQuery, labelFor]);

  const filteredRates = useMemo(() => {
    const q = bankQuery.trim().toLocaleLowerCase('tr-TR');
    if (!q) return rates;
    return rates.filter((r) =>
      (r.bankName || '').toLocaleLowerCase('tr-TR').includes(q)
      || (r.bankCode || '').toLocaleLowerCase('tr-TR').includes(q)
    );
  }, [rates, bankQuery]);

  // Sort by the chosen rate column in the chosen direction; rows missing that rate always sink to the end.
  const sortedRates = useMemo(() => {
    if (!sortBy) return filteredRates;
    const metric = (r) => {
      const buy = Number(r.buyRate), sell = Number(r.sellRate);
      const v = sortBy === 'spread' ? sell - buy : sortBy === 'sell' ? sell : buy;
      return Number.isFinite(v) ? v : null;
    };
    return [...filteredRates].sort((a, b) => {
      const av = metric(a), bv = metric(b);
      if (av == null && bv == null) return 0;
      if (av == null) return 1;
      if (bv == null) return -1;
      return sortDir === 'asc' ? av - bv : bv - av;
    });
  }, [filteredRates, sortBy, sortDir]);

  const lastUpdatedAt = useMemo(() => {
    if (rates.length === 0) return null;
    let latest = null;
    for (const r of rates) {
      if (!r.capturedAt) continue;
      const t = Date.parse(r.capturedAt);
      if (Number.isFinite(t) && (latest == null || t > latest)) latest = t;
    }
    return latest;
  }, [rates]);

  const isLoading = ratesLoading && rates.length === 0;

  // Keep the selected chip visible in the horizontal strip — otherwise it can sit scrolled off-screen while
  // its rates show below, leaving the strip looking disconnected from the current selection.
  const stripRef = useRef(null);
  // Let a plain mouse wheel pan the currency strip left/right (otherwise the wheel only scrolls the page).
  useWheelToHorizontal(stripRef);
  // Visible left/right scroll buttons for mouse users — shown only when the strip overflows that way.
  const [arrows, setArrows] = useState({ left: false, right: false });
  useEffect(() => {
    const el = stripRef.current;
    if (!el) return undefined;
    const update = () => setArrows({
      left: el.scrollLeft > 4,
      right: el.scrollLeft + el.clientWidth < el.scrollWidth - 4,
    });
    el.addEventListener('scroll', update, { passive: true });
    const ro = new ResizeObserver(update);
    ro.observe(el);
    const raf = requestAnimationFrame(update);
    return () => {
      el.removeEventListener('scroll', update);
      ro.disconnect();
      cancelAnimationFrame(raf);
    };
  }, [filteredCurrencies.length, kind]);
  useEffect(() => {
    const strip = stripRef.current;
    const activeChip = strip?.querySelector('[data-active="true"]');
    if (!strip || !activeChip) return;
    // Centre the active chip by adjusting ONLY the strip's own horizontal scroll. The previous scrollIntoView
    // also scrolled every ancestor (the whole page jumping when you switched currency/kind) — this contains the
    // motion to the chip rail.
    const target = activeChip.offsetLeft - (strip.clientWidth - activeChip.clientWidth) / 2;
    strip.scrollTo({ left: Math.max(0, target), behavior: 'smooth' });
  }, [currency, kind]);

  return (
    <div className="flex flex-col gap-4">
      <KindCurrencyControls
        t={t}
        kind={kind}
        setKind={setKind}
        currencyQuery={currencyQuery}
        setCurrencyQuery={setCurrencyQuery}
        detailRoute={detailRoute}
        labelFor={labelFor}
        currency={currency}
        setCurrency={setCurrency}
        filteredCurrencies={filteredCurrencies}
        orderedCurrencies={orderedCurrencies}
        currenciesLoading={currenciesLoading}
        arrows={arrows}
        stripRef={stripRef}
      />

      <div className="flex flex-col gap-4 min-w-0 w-full">
        <div className="flex items-center justify-between gap-3 flex-wrap">
          <div className="flex items-center gap-3 min-w-0">
            {kind === 'GOLD' ? (
              (() => {
                const gv = commodityVisual(currency);
                const GIcon = gv.Icon;
                return (
                  <GIcon className={`h-8 w-8 ${gv.color}`} strokeWidth={2} />
                );
              })()
            ) : flagEmoji(currency) ? (
              <span className="text-3xl leading-none">{flagEmoji(currency)}</span>
            ) : (
              <div className="flex items-center justify-center w-10 h-10 rounded-xl bg-accent/15 text-accent">
                <Banknote className="h-5 w-5" />
              </div>
            )}
            <div>
              <h2 className="text-lg font-bold text-fg">
                {labelFor(currency)}
              </h2>
              <p className="text-xs text-fg-muted font-mono flex flex-wrap items-center gap-x-2 gap-y-0.5">
                <span>
                  {t('bankRates.bankCount', { count: filteredRates.length })}
                  {bankQuery && filteredRates.length !== rates.length && (
                    <span className="opacity-70"> / {rates.length}</span>
                  )}
                </span>
                {lastUpdatedAt != null && (
                  <span className="inline-flex items-center gap-1 text-fg-subtle">
                    <Clock className="h-3 w-3" />
                    {t('bankRates.lastUpdated')}: {formatDateTimeShort(lastUpdatedAt, localeTag)}
                  </span>
                )}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2 flex-wrap w-full sm:w-auto">
            <div className="relative flex-1 sm:flex-none min-w-0">
              <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-fg-muted pointer-events-none" />
              <input
                type="text"
                value={bankQuery}
                onChange={(e) => setBankQuery(e.target.value)}
                maxLength={64}
                placeholder={t('bankRates.searchBank')}
                className="w-full sm:w-44 pl-8 pr-7 py-2 rounded-lg bg-bg-elevated border border-border-default text-xs text-fg placeholder:text-fg-muted focus:outline-none focus:border-accent/60"
              />
              {bankQuery && (
                <button
                  onClick={() => setBankQuery('')}
                  className="absolute right-1.5 top-1/2 -translate-y-1/2 text-fg-muted hover:text-fg p-0.5"
                  type="button"
                >
                  <X className="h-3 w-3" />
                </button>
              )}
            </div>
            <button
              onClick={() => refetch()}
              disabled={isFetching}
              className="flex items-center gap-2 px-3 py-2 rounded-lg border border-border-default bg-bg-elevated text-fg-muted hover:text-fg hover:bg-surface transition-all text-xs cursor-pointer disabled:opacity-50"
            >
              <RefreshCw className={`h-3.5 w-3.5 ${isFetching ? 'animate-spin' : ''}`} />
              {t('bankRates.refresh')}
            </button>
          </div>
        </div>

        {rates.length > 0 && (
          <div className="flex items-center gap-1.5 flex-wrap">
            <span className="inline-flex items-center gap-1 text-[10px] font-mono uppercase tracking-[0.14em] text-fg-muted mr-1">
              <ArrowUpDown className="h-3 w-3" />
              {t('bankRates.sort')}
            </span>
            {SORT_CHIPS.map((opt) => {
              const active = sortBy === opt.id;
              return (
                <button
                  key={opt.id}
                  type="button"
                  onClick={() => toggleSort(opt.id)}
                  className={`inline-flex items-center gap-1 text-[11px] font-mono font-semibold rounded-md px-2.5 py-1 cursor-pointer border-none transition-colors ${
                    active ? 'bg-accent/15 text-accent shadow-[inset_0_0_0_1px_rgba(99,102,241,0.4)]' : 'text-fg-muted hover:text-fg'
                  }`}
                >
                  {t(opt.labelKey)}
                  {active && (sortDir === 'desc' ? <ArrowDown className="h-3 w-3" /> : <ArrowUp className="h-3 w-3" />)}
                </button>
              );
            })}
          </div>
        )}

        {isLoading ? (
          <div className="flex-1 flex items-center justify-center">
            <Spinner size="lg" tone="accent" />
          </div>
        ) : filteredRates.length === 0 ? (
          <div className="flex-1 flex flex-col items-center justify-center gap-2 text-fg-muted">
            <Banknote className="h-10 w-10 opacity-40" />
            <p className="text-sm">{bankQuery ? t('bankRates.noMatch') : t('bankRates.emptyTitle')}</p>
            <p className="text-[11px] opacity-70">{bankQuery ? '' : t('bankRates.emptyHint')}</p>
          </div>
        ) : (
          <motion.div
            key={`${sortBy}-${sortDir}-${bankQuery.trim()}-${currency}-${kind}`}
            initial="hidden"
            animate="show"
            variants={{ hidden: {}, show: { transition: { staggerChildren: 0.03 } } }}
            className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3"
          >
            {sortedRates.map((row) => (
              <motion.div key={row.id} variants={{ hidden: { opacity: 0, y: 8 }, show: { opacity: 1, y: 0 } }}>
                <BankCard row={row} t={t} money={money} displayCurrency={displayCurrency} />
              </motion.div>
            ))}
          </motion.div>
        )}
      </div>
    </div>
  );
}
