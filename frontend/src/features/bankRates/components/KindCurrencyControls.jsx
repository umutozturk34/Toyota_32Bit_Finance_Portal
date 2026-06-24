import { Link } from 'react-router-dom';
import { ArrowUpRight, ChevronLeft, ChevronRight, Coins, DollarSign, Search, X } from 'lucide-react';
import Card from '../../../shared/components/card';
import Spinner from '../../../shared/components/feedback/Spinner';
import RateChip from './RateChip';

export default function KindCurrencyControls({
  t,
  kind,
  setKind,
  currencyQuery,
  setCurrencyQuery,
  detailRoute,
  labelFor,
  currency,
  setCurrency,
  filteredCurrencies,
  orderedCurrencies,
  currenciesLoading,
  arrows,
  stripRef,
}) {
  return (
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

        {detailRoute && (
          <Link
            to={detailRoute}
            className="inline-flex items-center gap-1 rounded-lg border border-accent/30 bg-accent/10 px-2.5 py-1.5 text-[11px] font-semibold text-accent no-underline transition-colors hover:bg-accent/15 sm:ml-auto"
          >
            {t('bankRates.viewDetail', { code: labelFor(currency) })} <ArrowUpRight className="h-3 w-3" />
          </Link>
        )}

        <span className={`w-full text-right shrink-0 text-[10px] font-mono text-fg-muted sm:w-auto ${detailRoute ? '' : 'sm:ml-auto'}`}>
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
  );
}
