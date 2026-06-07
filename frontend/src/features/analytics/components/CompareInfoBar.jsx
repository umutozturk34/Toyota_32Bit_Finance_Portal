import { Info } from 'lucide-react';
import { isMacro, isRateLike } from '../lib/compareSeriesUtils';
import { skipLeadingSplit } from '../lib/compareChartBuilder';
import { formatPrice } from '../../../shared/utils/formatters';

export default function CompareInfoBar({ selected, targetCurrency, commonStartDate, authoritativeReturns, t }) {
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
        // Base the % off the COMMON start date (the shared 0% baseline the chart normalizes from), not this
        // series' own first point — otherwise the info-bar % and chart % diverge whenever another series has
        // shorter history. Mirrors compareChartBuilder's baseIdx.
        const rawBaseIdx = commonStartDate ? sorted.findIndex((p) => p.date >= commonStartDate) : 0;
        const safeBase = rawBaseIdx >= 0 ? rawBaseIdx : 0;
        // Mirror compareChartBuilder EXACTLY: for a raw price series, advance the baseline past a leading
        // split-like cliff (fund launch-week crash / unadjusted split) so the headline % matches the chart
        // AND the inflation-beater, instead of basing on the pre-crash value (which understated PKZ ~6.4x —
        // the +600% vs the beater's real trailing return). Non-price series (portfolio/macro/bond) never split.
        const isPriceSeries = ind.type !== 'PORTFOLIO' && !isMacro(ind.type) && ind.type !== 'BOND';
        const baseIdx = isPriceSeries ? skipLeadingSplit(sorted, safeBase) : safeBase;
        const firstPoint = sorted.length > 0 ? sorted[baseIdx] : null;
        const lastValue = lastPoint?.value;
        const firstValue = firstPoint?.value;
        const isPortfolio = ind.type === 'PORTFOLIO';
        // Portfolio: % is the cumulative return (last/first of the index); the headline number is the
        // cumulative TL P&L carried alongside as pnlTry — "% to compare against inflation, ₺ to feel the money".
        const lastPnl = isPortfolio && lastPoint?.pnlTry != null ? Number(lastPoint.pnlTry) : null;
        let pct = (lastValue != null && firstValue != null && firstValue !== 0)
          ? ((Number(lastValue) - Number(firstValue)) / Math.abs(Number(firstValue))) * 100
          : null;
        // When opened from a Beater row, the table's cached (backend-computed) return is the source of truth
        // for the headline %; show it verbatim instead of the frontend re-compound so the two screens match
        // to the decimal. The plotted line keeps its own per-date values (visually within ~0.5pt).
        const authPct = authoritativeReturns?.[ind.code];
        if (authPct != null) pct = authPct;

        let formattedLast = '—';
        // True when formattedLast already carries the cumulative % (growth-index headline), so the
        // trailing pctText span must be suppressed to avoid rendering the same % twice.
        let headlineIsPct = false;
        if (isPortfolio) {
          formattedLast = lastPnl != null
            ? `${lastPnl > 0 ? '+' : ''}${formatPrice(lastPnl, { currency: targetCurrency })}`
            : '—';
        } else if (lastValue != null) {
          if (ind.type === 'MACRO_DEPOSIT' || ind.type === 'MACRO_RATE') {
            // Deposit / policy-reference rate (TLREF) is a compounded cumulative growth index; its
            // meaningful headline is the cumulative return %, not the raw annual rate level.
            formattedLast = pct != null ? `${pct > 0 ? '+' : ''}${pct.toFixed(2)}%` : '—';
            headlineIsPct = true;
          } else if (isRateLike(ind.type)) {
            formattedLast = ind.type === 'BOND' || isMacro(ind.type)
              ? `%${Number(lastValue).toFixed(2)}`
              : Number(lastValue).toLocaleString('tr-TR', { maximumFractionDigits: 2 });
          } else {
            // lastValue is already in targetCurrency (converted by ComparePage); format it directly
            // without re-converting through displayCurrency.
            formattedLast = formatPrice(lastValue, { currency: targetCurrency });
          }
        }

        let pctColor = '#94a3b8';
        let pctText = '';
        if (pct != null) {
          pctColor = pct > 0 ? '#10b981' : pct < 0 ? '#ef4444' : '#94a3b8';
          const sign = pct > 0 ? '+' : '';
          pctText = `${sign}${pct.toFixed(2)}%`;
        } else {
          pctText = '—';
        }
        // The growth-index headline (deposit / policy rate) already shows the cumulative % as
        // formattedLast, so blank the trailing pctText span to avoid printing the same % twice.
        if (headlineIsPct) pctText = '';

        // displayName is the single resolved label from compareSeriesUtils.displayLabel (commodity/macro
        // i18n, backend long name otherwise, "Portföy" placeholder for the id-only portfolio code).
        const friendlyName = ind.displayName || ind.name || ind.code;
        const showFriendly = friendlyName && friendlyName !== ind.code;
        return (
          <div key={`${ind.type}-${ind.code}`} className="flex items-baseline gap-1.5 sm:gap-2 text-[11px] flex-wrap min-w-0">
            <span className="h-1.5 w-1.5 rounded-full shrink-0 mt-1" style={{ background: color }} />
            {/* Portfolio "code" is a meaningless numeric DB id — never surface it; only its friendly name shows. */}
            {!isPortfolio && (
              <span className="font-mono text-[10px] text-fg-muted uppercase tracking-[0.12em] shrink-0">{ind.code}</span>
            )}
            {showFriendly && (
              <>
                {!isPortfolio && <span className="text-fg-subtle hidden sm:inline">·</span>}
                <span className="text-fg-muted truncate min-w-0 max-w-[140px] sm:max-w-[200px] md:max-w-none">
                  {friendlyName}
                </span>
              </>
            )}
            <span className="text-[10px] font-mono text-fg-subtle tracking-[0.04em] hidden sm:inline">{t(`marketOverview.macro.enum.${ind.type}`, { defaultValue: ind.type })}</span>
            <span className="ml-auto flex items-baseline gap-1.5 sm:gap-2 flex-wrap justify-end shrink-0">
              <span
                className="font-mono tabular-nums text-xs font-semibold text-fg whitespace-nowrap"
                style={{ color: (isPortfolio || headlineIsPct) ? pctColor : undefined }}
              >
                {formattedLast}
              </span>
              {pctText !== '' && (
                <span
                  className="font-mono tabular-nums text-[11px] font-bold tracking-tight whitespace-nowrap"
                  style={{ color: pctColor }}
                >
                  {pctText}
                </span>
              )}
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
