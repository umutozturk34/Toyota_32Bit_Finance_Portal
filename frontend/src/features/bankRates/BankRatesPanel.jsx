import { useCallback, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { Banknote, Clock, Coins, Crown, DollarSign, Gem, RefreshCw, Search, Sparkles, X } from 'lucide-react';
import { useBankRates, useBankRateCurrencies } from './hooks/useBankRates';
import Card from '../../shared/components/card';
import CurrencyMarker from '../../shared/components/currency/CurrencyMarker';
import Spinner from '../../shared/components/feedback/Spinner';
import { useMoney } from '../../shared/hooks/useMoney';
import { formatDateTimeShort } from '../../shared/utils/formatters';

const DEFAULT_CURRENCY_BY_KIND = { CURRENCY: 'USD', GOLD: 'GRAM_ALTIN' };

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

const GOLD_VISUAL = {
  GRAM_ALTIN:        { Icon: Coins,    color: 'text-amber-400' },
  CEYREK_ALTIN:      { Icon: Coins,    color: 'text-amber-400' },
  YARIM_ALTIN:       { Icon: Coins,    color: 'text-amber-400' },
  TAM_ALTIN:         { Icon: Coins,    color: 'text-amber-400' },
  BESLI_ALTIN:       { Icon: Coins,    color: 'text-amber-400' },
  CUMHURIYET_ALTINI: { Icon: Crown,    color: 'text-amber-500' },
  HAMIT_ALTIN:       { Icon: Crown,    color: 'text-amber-500' },
  RESAT_ALTIN:       { Icon: Crown,    color: 'text-amber-500' },
  ATA_ALTIN:         { Icon: Crown,    color: 'text-amber-500' },
  GRAM_HAS_ALTIN:    { Icon: Sparkles, color: 'text-yellow-400' },
  AYAR_14:           { Icon: Gem,      color: 'text-orange-400' },
  AYAR_18:           { Icon: Gem,      color: 'text-orange-400' },
  AYAR_22_BILEZIK:   { Icon: Gem,      color: 'text-orange-400' },
  GUMUS:             { Icon: Coins,    color: 'text-slate-300' },
};

function goldVisual(code) {
  return GOLD_VISUAL[code] || { Icon: Coins, tone: 'text-warning bg-warning/15' };
}

function formatRate(value, localeTag) {
  if (value == null) return '—';
  const num = Number(value);
  if (!Number.isFinite(num)) return '—';
  return num.toLocaleString(localeTag || 'tr-TR', { minimumFractionDigits: 4, maximumFractionDigits: 4 });
}

function BankCard({ row, t, localeTag, displayCurrency, money }) {
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
        <div className="text-[10px] text-fg-muted text-right font-mono">
          {t('bankRates.spread')}: {formatSpread(spread)}
        </div>
      )}
    </Card>
  );
}

function FilterItem({ active, label, code, count, onClick, showCode = true, kind }) {
  const isGold = kind === 'GOLD';
  const flag = isGold ? null : flagEmoji(code);
  const goldIcon = isGold ? goldVisual(code) : null;
  return (
    <button
      onClick={onClick}
      className={`w-full flex items-center gap-2 px-3 py-2 rounded-lg text-left text-sm transition-all border ${
        active
          ? 'bg-accent/15 border-accent/40 text-accent font-semibold'
          : 'bg-transparent border-transparent text-fg-muted hover:text-fg hover:bg-surface'
      }`}
    >
      {flag && <span className="shrink-0 text-lg leading-none">{flag}</span>}
      {goldIcon && (
        <goldIcon.Icon className={`shrink-0 h-5 w-5 ${goldIcon.color}`} strokeWidth={2} />
      )}
      <div className="min-w-0 flex-1">
        <p className="truncate">{label}</p>
        {showCode && (
          <p className="text-[10px] font-mono opacity-70">{code}</p>
        )}
      </div>
      {count != null && (
        <span className="shrink-0 text-[10px] font-mono bg-bg-elevated px-1.5 py-0.5 rounded">
          {count}
        </span>
      )}
    </button>
  );
}

export default function BankRatesPanel() {
  const { t } = useTranslation();
  const localeTag = t('common.localeTag');
  const labelFor = (code) => t(`bankRates.currency.${code}`, code);
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

  return (
    <div className="flex gap-4 items-start">
      <Card
        variant="elevated"
        radius="xl"
        padding="sm"
        backdropBlur
        className="w-60 shrink-0 flex flex-col gap-3 sticky top-4 self-start max-h-[calc(100vh-2rem)] overflow-hidden"
      >
        <div className="grid grid-cols-2 gap-1 p-1 rounded-lg bg-bg-base">
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

        <div className="flex items-center justify-between px-1 text-[10px] font-mono text-fg-muted">
          <span>
            {kind === 'GOLD'
              ? t('bankRates.goldCount', { count: filteredCurrencies.length, total: orderedCurrencies.length })
              : t('bankRates.currencyCount', { count: filteredCurrencies.length, total: orderedCurrencies.length })}
          </span>
        </div>

        <div className="relative">
          <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-fg-muted pointer-events-none" />
          <input
            type="text"
            value={currencyQuery}
            onChange={(e) => setCurrencyQuery(e.target.value)}
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

        <div className="flex flex-col gap-1 overflow-y-auto">
          {currenciesLoading && filteredCurrencies.length === 0 && (
            <div className="flex justify-center py-6"><Spinner size="sm" /></div>
          )}
          {filteredCurrencies.map((code) => (
            <FilterItem
              key={code}
              active={currency === code}
              code={code}
              label={labelFor(code)}
              showCode={kind !== 'GOLD'}
              kind={kind}
              onClick={() => setCurrency(code)}
            />
          ))}
          {filteredCurrencies.length === 0 && !currenciesLoading && (
            <p className="text-xs text-fg-muted text-center py-6">
              {currencyQuery ? t('bankRates.noMatch') : t('bankRates.noData')}
            </p>
          )}
        </div>
      </Card>

      <div className="flex-1 flex flex-col gap-4 min-w-0">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            {kind === 'GOLD' ? (
              (() => {
                const gv = goldVisual(currency);
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
          <div className="flex items-center gap-2">
            <div className="relative">
              <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-fg-muted pointer-events-none" />
              <input
                type="text"
                value={bankQuery}
                onChange={(e) => setBankQuery(e.target.value)}
                placeholder={t('bankRates.searchBank')}
                className="w-44 pl-8 pr-7 py-2 rounded-lg bg-bg-elevated border border-border-default text-xs text-fg placeholder:text-fg-muted focus:outline-none focus:border-accent/60"
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
            className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3"
          >
            {filteredRates.map((row) => (
              <motion.div key={row.id} variants={{ hidden: { opacity: 0, y: 8 }, show: { opacity: 1, y: 0 } }}>
                <BankCard row={row} t={t} localeTag={localeTag} money={money} displayCurrency={displayCurrency} />
              </motion.div>
            ))}
          </motion.div>
        )}
      </div>
    </div>
  );
}
