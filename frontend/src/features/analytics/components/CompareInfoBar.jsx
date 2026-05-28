import { Info } from 'lucide-react';
import { isMacro, isRateLike } from '../lib/compareSeriesUtils';

export default function CompareInfoBar({ selected, targetCurrency, money, t }) {
  return (
    <div className="rounded-lg border border-border-default/40 bg-bg-base/30 p-3 space-y-1.5">
      <div className="flex items-center gap-1.5 text-xs font-display font-semibold text-fg-muted">
        <Info className="h-3 w-3" />
        {t('marketOverview.macro.indicatorInfo', { defaultValue: 'Bilgi' })}
      </div>
      {selected.map(({ indicator: ind, color, points }) => {
        const sorted = points && points.length > 0
          ? [...points].sort((a, b) => String(a.date).localeCompare(String(b.date)))
          : [];
        const lastPoint = sorted.length > 0 ? sorted[sorted.length - 1] : null;
        const firstPoint = sorted.length > 0 ? sorted[0] : null;
        const lastValue = lastPoint?.value;
        const firstValue = firstPoint?.value;
        const pct = (lastValue != null && firstValue != null && firstValue !== 0)
          ? ((Number(lastValue) - Number(firstValue)) / Math.abs(Number(firstValue))) * 100
          : null;

        let formattedLast = '—';
        if (lastValue != null) {
          if (isRateLike(ind.type)) {
            formattedLast = ind.type === 'BOND' || isMacro(ind.type)
              ? `%${Number(lastValue).toFixed(2)}`
              : Number(lastValue).toLocaleString('tr-TR', { maximumFractionDigits: 2 });
          } else {
            formattedLast = money(lastValue, targetCurrency);
          }
        }

        let pctColor = '#94a3b8';
        let pctText = '—';
        if (pct != null) {
          pctColor = pct > 0 ? '#10b981' : pct < 0 ? '#ef4444' : '#94a3b8';
          const sign = pct > 0 ? '+' : '';
          pctText = `${sign}${pct.toFixed(2)}%`;
        }

        const friendlyName = ind.label
          ? t(`marketOverview.macro.${ind.label}`, { defaultValue: ind.name || ind.code })
          : (ind.name || ind.code);
        const showFriendly = friendlyName && friendlyName !== ind.code;
        return (
          <div key={`${ind.type}-${ind.code}`} className="flex items-baseline gap-1.5 sm:gap-2 text-[11px] flex-wrap min-w-0">
            <span className="h-1.5 w-1.5 rounded-full shrink-0 mt-1" style={{ background: color }} />
            <span className="font-mono text-[10px] text-fg-muted uppercase tracking-[0.12em] shrink-0">{ind.code}</span>
            {showFriendly && (
              <>
                <span className="text-fg-subtle hidden sm:inline">·</span>
                <span className="text-fg-muted truncate min-w-0 max-w-[140px] sm:max-w-[200px] md:max-w-none">
                  {friendlyName}
                </span>
              </>
            )}
            <span className="text-[10px] font-mono text-fg-subtle tracking-[0.04em] hidden sm:inline">{t(`marketOverview.macro.enum.${ind.type}`, { defaultValue: ind.type })}</span>
            <span className="ml-auto flex items-baseline gap-1.5 sm:gap-2 flex-wrap justify-end shrink-0">
              <span className="font-mono tabular-nums text-xs font-semibold text-fg whitespace-nowrap">{formattedLast}</span>
              <span
                className="font-mono tabular-nums text-[11px] font-bold tracking-tight whitespace-nowrap"
                style={{ color: pctColor }}
              >
                {pctText}
              </span>
              {lastPoint?._filled && (
                <span className="text-[9px] font-mono text-fg-subtle italic" title={t('analytics.compareInfo.forwardFilledTooltip')}>↗</span>
              )}
              {sorted.some((p) => p._backfilled) && (
                <span className="text-[9px] font-mono text-fg-subtle italic" title={t('analytics.compareInfo.backfilledTooltip')}>↩</span>
              )}
            </span>
          </div>
        );
      })}
    </div>
  );
}
