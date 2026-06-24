import { X, Landmark } from 'lucide-react';

/**
 * The modal header: series code title, optional name/ISIN subtitles, the CPI + colour-coded bond-type badges,
 * and the close button. Pure presentation — `t`, the derived flags and `typeColor` are passed in so the badge
 * styling matches the parent exactly.
 */
export default function BondDetailHeader({ bond, isCpi, typeColor, onClose, t }) {
  return (
    <div className="flex items-start justify-between gap-3 px-4 sm:px-6 pt-4 sm:pt-5 pb-3 shrink-0">
      <div className="flex items-center gap-3 min-w-0">
        <span className="flex items-center justify-center w-10 h-10 rounded-xl bg-accent/10 text-accent shrink-0">
          <Landmark className="h-5 w-5" />
        </span>
        <div className="min-w-0">
          <h2 className="text-base sm:text-lg font-bold text-fg leading-tight font-mono truncate">{bond.bondSeriesCode}</h2>
          {bond.bondName && bond.bondName !== bond.bondSeriesCode && (
            <p className="text-xs text-fg-muted truncate">{bond.bondName}</p>
          )}
          {bond.bondIsin && (
            <p className="text-[10px] text-fg-subtle font-mono truncate">{bond.bondIsin}</p>
          )}
        </div>
      </div>
      <div className="flex items-center gap-2 shrink-0">
        {isCpi && (
          <span className="rounded-lg border px-2 py-1 text-[10px] font-semibold tracking-wider bg-warning/15 text-warning border-warning/25">
            {t('portfolio.bonds.coupon.cpiLinked')}
          </span>
        )}
        {bond.bondType && (
          <span className={`rounded-lg border px-2 py-1 text-[10px] font-semibold tracking-wider ${typeColor}`}>
            {t(`market.bond.types.${bond.bondType}`, { defaultValue: bond.bondType })}
          </span>
        )}
        <button
          onClick={onClose}
          aria-label={t('common.close')}
          className="flex items-center justify-center w-8 h-8 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}
