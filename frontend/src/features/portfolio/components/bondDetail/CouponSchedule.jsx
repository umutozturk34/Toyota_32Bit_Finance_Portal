import { CheckCircle2, CircleDot, MinusCircle } from 'lucide-react';

const SCHEDULE_STYLE = {
  received: { box: 'bg-success/5 border border-success/15', Icon: CheckCircle2, icon: 'text-success', label: 'text-success' },
  upcoming: { box: 'bg-accent/5 border border-accent/15', Icon: CircleDot, icon: 'text-accent', label: 'text-accent' },
  beforeEntry: { box: 'bg-bg-base/40', Icon: MinusCircle, icon: 'text-fg-subtle', label: 'text-fg-subtle' },
};

const STATUS_KEY = { RECEIVED: 'received', BEFORE_ENTRY: 'beforeEntry', UPCOMING: 'upcoming' };

/**
 * The backend-computed coupon schedule list (each coupon priced at its own historical rate). Pure presentation —
 * `t`, `money` and `formatDate` are passed in so the rows render identically to the parent modal.
 */
export default function CouponSchedule({ schedule, isCpi, isPerUnit, indexNoteKey, localeTag, money, formatDate, t }) {
  return (
    <section className="rounded-2xl border border-border-default bg-bg-base/50 p-3.5">
      <h3 className="text-xs font-semibold uppercase tracking-wider text-fg-muted mb-1">
        {t('portfolio.bonds.detail.scheduleTitle')}
      </h3>
      <p className="text-[11px] text-fg-subtle mb-3">{t('portfolio.bonds.detail.scheduleHint')}</p>
      {schedule.length === 0 ? (
        <p className="text-xs text-fg-muted">
          {(isCpi || isPerUnit) ? t(indexNoteKey) : t('portfolio.bonds.detail.scheduleEmpty')}
        </p>
      ) : (
        <>
          {(isCpi || isPerUnit) && (
            <p className="text-[11px] text-fg-muted mb-2">{t(indexNoteKey)}</p>
          )}
          <ul className="space-y-1.5 max-h-56 overflow-y-auto pr-1">
          {schedule.map((c) => {
            const sKey = STATUS_KEY[c.status] || 'upcoming';
            const style = SCHEDULE_STYLE[sKey];
            const StatusIcon = style.Icon;
            const statusLabel = sKey === 'received'
              ? t('portfolio.bonds.coupon.scheduleReceived')
              : sKey === 'beforeEntry'
                ? t('portfolio.bonds.coupon.scheduleBeforeEntry')
                : t('portfolio.bonds.detail.coupon.upcoming');
            return (
              <li
                key={c.date}
                className={`flex items-center justify-between gap-3 rounded-lg px-3 py-2 text-xs ${style.box}`}
              >
                <span className="flex items-center gap-2 min-w-0">
                  <StatusIcon className={`h-3.5 w-3.5 shrink-0 ${style.icon}`} />
                  <span className={`font-mono truncate ${sKey === 'upcoming' ? 'text-fg font-medium' : 'text-fg-muted'}`}>
                    {formatDate(c.date, localeTag)}
                  </span>
                  {c.ratePer100 != null && (
                    <span className="font-mono text-[10px] text-fg-subtle shrink-0">%{Number(c.ratePer100).toFixed(2)}</span>
                  )}
                </span>
                <span className="flex items-center gap-2 shrink-0">
                  <span className={`font-mono ${sKey === 'received' ? 'text-success' : 'text-fg-muted'}`}>
                    {money(c.amountTry, 'TRY')}
                  </span>
                  <span className={`text-[10px] font-medium tracking-wider uppercase ${style.label}`}>{statusLabel}</span>
                </span>
              </li>
            );
          })}
          </ul>
        </>
      )}
    </section>
  );
}
