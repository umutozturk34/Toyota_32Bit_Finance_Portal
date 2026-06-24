import { Tag, RotateCcw } from 'lucide-react';
import { MAX_BOND_PRICE_TRY } from '../../../../shared/utils/numberInput';

/**
 * The clean-price (per-100) input plus its entry-day suggestion band and the CPI uplift hint. Pure presentation:
 * `money` and `t` are passed in so the locked-base TRY formatting matches the parent exactly (re-deriving useMoney
 * here would risk converting the per-100 price into the global display currency).
 */
export default function EntryPriceField({
  t, money, priceValue, priceBand, suggestedPrice, isCpi, baseIndex,
  onPriceChange, onPriceBlur, onReset,
}) {
  return (
    <div className="space-y-1.5">
      <label className="text-xs font-medium text-fg-muted flex items-center justify-between gap-1.5">
        <span className="inline-flex items-center gap-1.5">
          <Tag className="h-3 w-3" />
          {t('portfolio.bonds.form.fields.entryPrice')}
        </span>
        <span className="font-mono text-[10px] uppercase tracking-wider text-accent">
          {t('portfolio.bonds.form.fields.perUnit', { defaultValue: '1 adet' })}
        </span>
      </label>
      <div className="relative">
        <span className="absolute left-3 top-1/2 -translate-y-1/2 text-fg-subtle font-mono text-sm pointer-events-none">₺</span>
        <input
          type="number"
          step="any"
          min="0"
          max={MAX_BOND_PRICE_TRY}
          inputMode="decimal"
          value={priceValue}
          onChange={onPriceChange}
          onBlur={onPriceBlur}
          placeholder="0.00"
          className="w-full rounded-lg border border-border-default bg-bg-base pl-7 pr-3 py-2.5 text-sm text-fg font-mono placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
        />
      </div>
      {priceBand ? (
        <div className="space-y-1 rounded-lg border border-accent/20 bg-accent/5 px-2.5 py-1.5 text-[11px]">
          <p className="flex items-start gap-1.5 text-fg-muted leading-snug">
            <Tag className="h-3 w-3 text-accent shrink-0 mt-0.5" />
            <span className="min-w-0 break-words">
              {t('portfolio.bonds.form.fields.priceSuggested', {
                price: money(suggestedPrice, 'TRY'),
                low: money(priceBand[0], 'TRY'),
                high: money(priceBand[1], 'TRY'),
              })}
            </span>
          </p>
          <button
            type="button"
            onClick={onReset}
            className="inline-flex items-center gap-1 font-mono text-[10px] uppercase tracking-wider text-accent hover:text-accent-bright bg-transparent border-none cursor-pointer p-0"
          >
            <RotateCcw className="h-3 w-3" />
            {t('portfolio.bonds.form.fields.priceReset')}
          </button>
        </div>
      ) : (
        <p className="text-[10px] text-fg-subtle">{t('portfolio.bonds.form.fields.priceHint')}</p>
      )}
      {isCpi && (
        <div className="flex items-start gap-1.5 rounded-lg border border-warning/25 bg-warning/10 px-2.5 py-1.5 text-[11px] text-warning/90">
          <Tag className="h-3 w-3 shrink-0 mt-0.5" />
          <span>{t('portfolio.bonds.form.cpiPriceHint', { index: baseIndex != null ? Math.round(Number(baseIndex)).toLocaleString() : '6300' })}</span>
        </div>
      )}
    </div>
  );
}
