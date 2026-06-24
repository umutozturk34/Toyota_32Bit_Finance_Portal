import { X, Landmark } from 'lucide-react';

/**
 * The modal header: title, the picked series subtitle, the colour-coded bond-type badge, and the close button.
 * Pure presentation — `t` and the derived type flags are passed in so the badge styling matches the parent exactly.
 */
export default function BondFormHeader({
  t, isEdit, seriesName, seriesCode, bondType, isCpi, isFloating, isDiscount, dismissable, onClose,
}) {
  return (
    <div className="flex items-center justify-between px-4 sm:px-6 pt-4 sm:pt-6 pb-4 shrink-0">
      <div className="flex items-center gap-3">
        <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-accent/10">
          <Landmark className="h-4 w-4 text-accent" />
        </div>
        <div>
          <h2 className="text-base font-semibold text-fg">
            {isEdit ? t('portfolio.bonds.form.titleEdit') : t('portfolio.bonds.form.titleAdd')}
          </h2>
          <p className="text-xs text-fg-muted">{seriesName || seriesCode || t('portfolio.bonds.form.subtitle')}</p>
          {bondType && (
            <span className={`mt-1 inline-flex items-center rounded px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wider ring-1 ring-inset ${
              isCpi ? 'bg-warning/15 text-warning ring-warning/25'
                : isFloating ? 'bg-accent/12 text-accent ring-accent/25'
                  : isDiscount ? 'bg-fg-muted/10 text-fg-muted ring-border-default/50'
                    : 'bg-success/12 text-success ring-success/25'
            }`}>
              {t(`market.bond.types.${bondType}`, { defaultValue: bondType })}
            </span>
          )}
        </div>
      </div>
      <button
        onClick={onClose}
        disabled={!dismissable}
        aria-label={t('common.close')}
        className="flex items-center justify-center w-8 h-8 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer disabled:opacity-30"
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  );
}
