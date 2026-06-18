import { Percent, Repeat, Lock } from 'lucide-react';

// User-selectable coupon cadences (a discount bill is always ZERO_COUPON and shows no selector).
const FREQ_OPTIONS = ['ANNUAL', 'SEMI_ANNUAL', 'QUARTERLY', 'MONTHLY'];

/**
 * The coupon panel: a read-only published per-period (.ORAN) rate sourced from the bond record plus the
 * user-overridable payment-frequency selector. For a discount bill (couponHidden) only the discount note shows.
 * Pure presentation — `t` and every derived flag/value are passed in so render and i18n match the parent exactly.
 */
export default function CouponSection({
  t, couponHidden, couponPerPeriod, couponFrequency, freqAutoDetect,
  publishedAnnualRate, isCpi, isFloating, onSelectFrequency,
}) {
  if (couponHidden) {
    return (
      <div className="flex items-start gap-2 rounded-lg border border-border-default bg-bg-base/60 px-3 py-2.5 text-[11px] text-fg-muted">
        <Percent className="h-3.5 w-3.5 shrink-0 mt-0.5" />
        <span>{t('portfolio.bonds.form.discountNote')}</span>
      </div>
    );
  }
  return (
    <div className="space-y-2.5 rounded-xl border border-border-default bg-bg-base/50 p-3">
      {/* Coupon RATE — read-only, the bond's published per-period (.ORAN) value */}
      <div className="flex items-center justify-between gap-2">
        <span className="inline-flex items-center gap-1.5 text-xs font-medium text-fg-muted">
          <Percent className="h-3.5 w-3.5 text-accent" />
          {t('portfolio.bonds.form.fields.couponRate')}
        </span>
        <span className="font-mono text-sm font-semibold text-fg">
          {couponPerPeriod != null ? `%${couponPerPeriod.toFixed(2)}` : '—'}
          <span className="ml-1 text-[10px] font-normal text-fg-subtle">{t('portfolio.bonds.form.perPeriod')}</span>
        </span>
      </div>
      <p className="flex items-center gap-1.5 text-[10px] text-fg-subtle">
        <Lock className="h-3 w-3 shrink-0" />
        {t('portfolio.bonds.form.couponFromDb')}
      </p>
      {/* Coupon FREQUENCY — user-overridable (the data carries no frequency; default inferred from type) */}
      <div className="space-y-1.5 pt-1">
        <span className="inline-flex items-center gap-1.5 text-xs font-medium text-fg-muted">
          <Repeat className="h-3.5 w-3.5 text-accent" />
          {t('portfolio.bonds.detail.frequencyLabel')}
        </span>
        <div className="grid grid-cols-2 gap-1 rounded-lg bg-bg-base/60 p-1 ring-1 ring-inset ring-border-default/50">
          {FREQ_OPTIONS.map((f) => {
            const selected = couponFrequency === f;
            return (
              <button
                key={f}
                type="button"
                onClick={() => onSelectFrequency(f)}
                aria-pressed={selected}
                className={`rounded-md px-1.5 py-1.5 text-[11px] font-medium transition-colors border-none cursor-pointer ${
                  selected
                    ? 'text-accent bg-accent/15 ring-1 ring-inset ring-accent/40'
                    : 'text-fg-muted bg-transparent hover:text-fg hover:bg-surface/60'
                }`}
              >
                {t(`portfolio.bonds.coupon.freq.${f}`)}
              </button>
            );
          })}
        </div>
        <div className="flex items-center justify-between gap-2 text-[10px] text-fg-subtle">
          <span>{t(freqAutoDetect ? 'portfolio.bonds.form.freqAutoDetect' : 'portfolio.bonds.form.freqInferred')}</span>
          {publishedAnnualRate != null && (
            <span className="font-mono text-accent shrink-0">{t('portfolio.bonds.form.annualApprox', { rate: publishedAnnualRate.toFixed(2) })}</span>
          )}
        </div>
      </div>
      {isCpi && (
        <p className="rounded-lg bg-warning/10 px-2.5 py-1.5 text-[11px] text-warning/90">
          {t('portfolio.bonds.form.cpiCouponNote')}
        </p>
      )}
      {isFloating && (
        <p className="rounded-lg bg-accent/[0.08] px-2.5 py-1.5 text-[11px] text-accent/90">
          {t('portfolio.bonds.form.floatingNote')}
        </p>
      )}
    </div>
  );
}
