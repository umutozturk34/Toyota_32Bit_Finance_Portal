/**
 * The total-cost callout (price × quantity, always stored in TRY). Pure presentation — `money`/`formatCompact`
 * come from the parent's locked-base useMoney so the per-100 TRY value is never converted to the display currency.
 */
export default function TotalCostBanner({ t, money, formatCompact, totalCostTry }) {
  return (
    <div className="sm:col-span-2 rounded-xl border border-accent/30 bg-gradient-to-r from-accent/5 to-transparent px-4 py-3 flex items-center justify-between gap-3 min-w-0">
      <div className="flex flex-col gap-0.5 min-w-0">
        <span className="text-xs font-semibold text-accent">{t('portfolio.bonds.form.totalCost')}</span>
        <span className="text-[10px] font-mono uppercase tracking-wider text-fg-subtle">
          {t('portfolio.bonds.form.storedAsTry')}
        </span>
      </div>
      <span
        className="text-lg font-bold font-mono text-accent truncate"
        title={money(totalCostTry, 'TRY')}
      >
        {formatCompact(totalCostTry, 'TRY', 1_000_000_000)}
      </span>
    </div>
  );
}
