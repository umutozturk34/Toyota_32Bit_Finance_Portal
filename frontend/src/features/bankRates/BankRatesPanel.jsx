import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { ArrowDown, ArrowUp, ArrowUpDown, Banknote, ChevronLeft, ChevronRight, Clock, Coins, DollarSign, RefreshCw, Search, X } from 'lucide-react';
import { useBankRates, useBankRateCurrencies } from './hooks/useBankRates';
import Card from '../../shared/components/card';
import CurrencyMarker from '../../shared/components/currency/CurrencyMarker';
import Spinner from '../../shared/components/feedback/Spinner';
import { useMoney } from '../../shared/hooks/useMoney';
import useWheelToHorizontal from '../../shared/hooks/useWheelToHorizontal';
import { formatDateTimeShort } from '../../shared/utils/formatters';
import { commodityVisual } from '../../shared/icons/commodities';

const DEFAULT_CURRENCY_BY_KIND = { CURRENCY: 'USD', GOLD: 'GRAM_ALTIN' };

// Sort the bank list by a rate column; clicking an active chip flips its arrow, mirroring the returns page.
const SORT_CHIPS = [
  { id: 'buy', labelKey: 'bankRates.sortBuy' },
  { id: 'sell', labelKey: 'bankRates.sortSell' },
  { id: 'spread', labelKey: 'bankRates.sortSpread' },
];
// First-click direction per column: highest buy, cheapest sell, tightest spread.
const SORT_DEFAULT_DIR = { buy: 'desc', sell: 'asc', spread: 'asc' };

const CURRENCY_COUNTRY = {
  USD: 'US', EUR: 'EU', GBP: 'GB', CHF: 'CH', CAD: 'CA', AUD: 'AU', JPY: 'JP', SAR: 'SA',
  CNY: 'CN', DKK: 'DK', SEK: 'SE', NOK: 'NO', RUB: 'RU', QAR: 'QA', KWD: 'KW', AED: 'AE',
  ZAR: 'ZA', HKD: 'HK', PLN: 'PL', RON: 'RO', SGD: 'SG', NZD: 'NZ', CZK: 'CZ', HUF: 'HU',
  INR: 'IN', THB: 'TH', MXN: 'MX', BRL: 'BR',
};

function flagEmoji(currencyCode) {
  const cc = CURRENCY_COUNTRY[currencyCode];
  if (!cc) return null;
  if (cc === 'EU') return '🇪🇺';
  return String.fromCodePoint(...cc.split('').map(c => 0x1F1E6 + c.charCodeAt(0) - 'A'.charCodeAt(0)));
}

function BankCard({ row, t, displayCurrency, money }) {
  const spread = row.buyRate != null && row.sellRate != null
    ? Number(row.sellRate) - Number(row.buyRate)
    : null;
  const isMarket = row.bankCode === 'MARKET';
  const displayName = isMarket ? t('bankRates.marketBank') : row.bankName;
  const formatValue = (v) => v == null ? '—' : money(v);
  const formatSpread = (s) => s == null ? null : money(s);
  const marker = displayCurrency === 'ORIGINAL' ? 'TRY' : displayCurrency;
  return (
    <Card
      variant="elevated"
      radius="xl"
      padding="md"
      backdropBlur
      className="flex flex-col gap-3"
    >
      <div className="flex items-center gap-3">
        {row.bankLogoUrl ? (
          <img
            src={row.bankLogoUrl}
            alt={displayName}
            className="w-10 h-10 rounded-lg object-contain"
            onError={(e) => { e.target.style.display = 'none'; }}
          />
        ) : (
          <div className={`w-10 h-10 rounded-lg flex items-center justify-center text-xs font-bold ${
            isMarket ? 'bg-warning/15 text-warning' : 'bg-accent/10 text-accent'
          }`}>
            {isMarket ? <Coins className="h-5 w-5" /> : (row.bankCode || '?').slice(0, 3).toUpperCase()}
          </div>
        )}
        <div className="min-w-0">
          <p className="text-sm font-semibold text-fg truncate">{displayName}</p>
          {isMarket && (
            <p className="text-[10px] text-fg-muted truncate">{t('bankRates.marketHint')}</p>
          )}
        </div>
      </div>
      <div className="grid grid-cols-2 gap-2">
        <div className="rounded-lg border border-success/25 bg-success/5 px-2.5 py-2">
          <p className="text-[10px] text-success uppercase tracking-wide font-medium flex items-center gap-1">
            {t('bankRates.buy')} <CurrencyMarker code={marker} />
          </p>
          <p className="text-sm font-mono font-bold text-success">{formatValue(row.buyRate)}</p>
        </div>
        <div className="rounded-lg border border-danger/25 bg-danger/5 px-2.5 py-2">
          <p className="text-[10px] text-danger uppercase tracking-wide font-medium flex items-center gap-1">
            {t('bankRates.sell')} <CurrencyMarker code={marker} />
          </p>
          <p className="text-sm font-mono font-bold text-danger">{formatValue(row.sellRate)}</p>
        </div>
      </div>
      {spread != null && (
        <div className="flex items-center justify-between pt-0.5 font-mono text-[10px]">
          <span className="uppercase tracking-wide text-fg-subtle">{t('bankRates.spread')}</span>
          <span className="font-semibold tabular-nums text-fg-muted">{formatSpread(spread)}</span>
        </div>
      )}
    </Card>
  );
}

// A single compact chip in the horizontal currency/gold strip: flag + ISO code for currencies, gold glyph +
// localized name for gold (the gold "codes" aren't user-facing). The full currency name lives in the title and
// in the detail header below. shrink-0 keeps chips from squishing so the strip scrolls horizontally instead.
function RateChip({ active, code, label, onClick, kind }) {
  const isGold = kind === 'GOLD';
  const flag = isGold ? null : flagEmoji(code);
  const goldIcon = isGold ? commodityVisual(code) : null;
  return (
    <button
      onClick={onClick}
      title={label}
      data-active={active ? 'true' : undefined}
      className={`shrink-0 inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-semibold border cursor-pointer transition-all ${
        active
          ? 'bg-accent/15 border-accent/40 text-accent'
          : 'bg-transparent border-border-default text-fg-muted hover:text-fg hover:border-border-hover'
      }`}
    >
      {flag && <span className="shrink-0 text-base leading-none">{flag}</span>}
      {goldIcon && <goldIcon.Icon className={`shrink-0 h-4 w-4 ${goldIcon.color}`} />}
      <span className="whitespace-nowrap">{isGold ? label : code}</span>
    </button>
  );
}

export default function BankRatesPanel() {
  const { t } = useTranslation();
  const localeTag = t('common.localeTag');
  const labelFor = useCallback((code) => t(`bankRates.currency.${code}`, code), [t]);
  const { format: money, currency: displayCurrency } = useMoney();
  const [searchParams, setSearchParams] = useSearchParams();
  const kindParam = searchParams.get('kind');
  const kind = kindParam === 'GOLD' ? 'GOLD' : 'CURRENCY';
  const currency = searchParams.get('rate') || DEFAULT_CURRENCY_BY_KIND[kind];
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
      'GRAM_ALTIN', 'CEYREK_ALTIN', 'YARIM_ALTIN', 'TAM_ALTIN', 'ATA_ALTIN',
      'CUMHURIYET_ALTINI', 'HAMIT_ALTIN', 'RESAT_ALTIN', 'BESLI_ALTIN', 'GRAM_HAS_ALTIN',
      'AYAR_14', 'AYAR_18', 'AYAR_22_BILEZIK', 'GUMUS',
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
    const activeChip = stripRef.current?.querySelector('[data-active="true"]');
    activeChip?.scrollIntoView({ inline: 'center', block: 'nearest', behavior: 'smooth' });
  }, [currency, kind]);

  return (
    <div className="flex flex-col gap-4">
      <Card
        variant="elevated"
        radius="xl"
        padding="sm"
        backdropBlur
        className="flex flex-col gap-2.5"
      >
        <div className="flex items-center gap-2 flex-wrap">
          <div className="grid grid-cols-2 gap-1 p-1 rounded-lg bg-bg-base shrink-0">
            <button
              onClick={() => setKind('CURRENCY')}
              className={`flex items-center justify-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold transition-all border-none ${
                kind === 'CURRENCY'
                  ? 'bg-accent text-white shadow-sm shadow-accent/30'
                  : 'bg-transparent text-fg-muted hover:text-fg'
              }`}
            >
              <DollarSign className="h-3 w-3" /> {t('bankRates.tabCurrency')}
            </button>
            <button
              onClick={() => setKind('GOLD')}
              className={`flex items-center justify-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold transition-all border-none ${
                kind === 'GOLD'
                  ? 'bg-warning text-white shadow-sm shadow-warning/30'
                  : 'bg-transparent text-fg-muted hover:text-fg'
              }`}
            >
              <Coins className="h-3 w-3" /> {t('bankRates.tabGold')}
            </button>
          </div>

          <div className="relative flex-1 min-w-0 sm:flex-none sm:w-56">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-fg-muted pointer-events-none" />
            <input
              type="text"
              value={currencyQuery}
              onChange={(e) => setCurrencyQuery(e.target.value)}
              maxLength={64}
              placeholder={t('bankRates.searchCurrency')}
              className="w-full pl-8 pr-7 py-1.5 rounded-lg bg-bg-base border border-border-default text-xs text-fg placeholder:text-fg-muted focus:outline-none focus:border-accent/60"
            />
            {currencyQuery && (
              <button
                onClick={() => setCurrencyQuery('')}
                className="absolute right-1.5 top-1/2 -translate-y-1/2 text-fg-muted hover:text-fg p-0.5"
                type="button"
              >
                <X className="h-3 w-3" />
              </button>
            )}
          </div>

          <span className="w-full text-right sm:w-auto sm:ml-auto shrink-0 text-[10px] font-mono text-fg-muted">
            {kind === 'GOLD'
              ? t('bankRates.goldCount', { count: filteredCurrencies.length, total: orderedCurrencies.length })
              : t('bankRates.currencyCount', { count: filteredCurrencies.length, total: orderedCurrencies.length })}
          </span>
        </div>

        <div className="relative">
          {arrows.left && (
            <button
              type="button"
              onClick={() => stripRef.current?.scrollBy({ left: -260, behavior: 'smooth' })}
              aria-label={t('bankRates.scrollLeft', { defaultValue: 'Sola kaydır' })}
              className="absolute left-0 top-1/2 -translate-y-1/2 z-10 flex items-center justify-center h-7 w-7 rounded-full bg-bg-elevated/95 border border-border-default text-fg-muted hover:text-fg hover:border-accent/50 shadow-md backdrop-blur-sm cursor-pointer transition-colors"
            >
              <ChevronLeft className="h-4 w-4" />
            </button>
          )}
          <div ref={stripRef} className="flex items-center gap-1.5 overflow-x-auto scrollbar-hide pb-1 -mb-1">
            {currenciesLoading && filteredCurrencies.length === 0 && (
              <div className="flex justify-center py-2 w-full"><Spinner size="sm" /></div>
            )}
            {filteredCurrencies.map((code) => (
              <RateChip
                key={code}
                active={currency === code}
                code={code}
                label={labelFor(code)}
                kind={kind}
                onClick={() => setCurrency(code)}
              />
            ))}
            {filteredCurrencies.length === 0 && !currenciesLoading && (
              <p className="text-xs text-fg-muted py-2">
                {currencyQuery ? t('bankRates.noMatch') : t('bankRates.noData')}
              </p>
            )}
          </div>
          {arrows.right && (
            <button
              type="button"
              onClick={() => stripRef.current?.scrollBy({ left: 260, behavior: 'smooth' })}
              aria-label={t('bankRates.scrollRight', { defaultValue: 'Sağa kaydır' })}
              className="absolute right-0 top-1/2 -translate-y-1/2 z-10 flex items-center justify-center h-7 w-7 rounded-full bg-bg-elevated/95 border border-border-default text-fg-muted hover:text-fg hover:border-accent/50 shadow-md backdrop-blur-sm cursor-pointer transition-colors"
            >
              <ChevronRight className="h-4 w-4" />
            </button>
          )}
        </div>
      </Card>

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
