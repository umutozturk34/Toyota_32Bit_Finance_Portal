import { Info } from 'lucide-react';
import { isRateLike, compareTypeLabel, seriesWindowPct } from '../lib/compareSeriesUtils';
import { formatPercentSmart, fitMoney } from '../../../shared/utils/formatters';
import { moneyDigits } from '../utils';

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
        const lastValue = lastPoint?.value;
        const isPortfolio = ind.type === 'PORTFOLIO';
        // Portfolio: % is the cumulative return (last/first of the index); the headline number is the
        // cumulative TL P&L carried alongside as pnlTry — "% to compare against inflation, ₺ to feel the money".
        const lastPnl = isPortfolio && lastPoint?.pnlTry != null ? Number(lastPoint.pnlTry) : null;
        // Headline % over the shared window via the single shared source — it also applies the Beater-cached
        // (backend) return override so this row, the chart line and the verdict hero all agree to the decimal.
        const pct = seriesWindowPct(ind, points, commonStartDate, authoritativeReturns);

        let formattedLast = '—';
        // True when formattedLast already carries the cumulative % (growth-index headline), so the
        // trailing pctText span must be suppressed to avoid rendering the same % twice.
        let headlineIsPct = false;
        if (isPortfolio) {
          // Adaptive maxDecimals mirrors compareChartBuilder.formatPnl so the chart tooltip and this info-bar
          // print the same P&L to the digit, including sub-cent values that would otherwise show as ₺0,00.
          formattedLast = lastPnl != null
            ? `${lastPnl > 0 ? '+' : ''}${fitMoney(lastPnl, { currency: targetCurrency, maxChars: 16, maxDecimals: Math.max(2, moneyDigits(lastPnl)) })}`
            : '—';
        } else if (lastValue != null) {
          if (ind.type === 'MACRO_DEPOSIT' || ind.type === 'MACRO_RATE') {
            // Deposit / policy-reference rate (TLREF) is a compounded cumulative growth index; its
            // meaningful headline is the cumulative return %, not the raw annual rate level.
            formattedLast = pct != null ? formatPercentSmart(pct) : '—';
            headlineIsPct = true;
          } else if (isRateLike(ind.type)) {
            // BOND carries a yield percentage, so it prints with a % sign. CPI/PPI (MACRO_INFLATION) reaching
            // here is a cumulative INDEX level (2003=100, e.g. ~4.028), not a rate — show it as a plain value,
            // never "%4028". Math.max(2, …) keeps 2 decimals normally while a sub-cent level borrows precision.
            formattedLast = ind.type === 'BOND'
              ? `%${Number(lastValue).toFixed(2)}`
              : Number(lastValue).toLocaleString('tr-TR', { maximumFractionDigits: Math.max(2, moneyDigits(Number(lastValue))) });
          } else {
            // lastValue is already in targetCurrency (converted by ComparePage); format it directly
            // without re-converting through displayCurrency. Adaptive maxDecimals keeps normal output
            // identical yet stops a tiny-but-nonzero value from showing as "0 €" / "₺0,00".
            formattedLast = fitMoney(lastValue, { currency: targetCurrency, maxChars: 16, maxDecimals: Math.max(2, moneyDigits(Number(lastValue))) });
          }
        }

        let pctColor = '#94a3b8';
        let pctText = '';
        if (pct != null) {
          pctColor = pct > 0 ? '#10b981' : pct < 0 ? '#ef4444' : '#94a3b8';
          pctText = formatPercentSmart(pct);
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
            <span className="text-[10px] font-mono text-fg-subtle tracking-[0.04em] hidden sm:inline">{compareTypeLabel(t, ind.type)}</span>
            <span className="ml-auto flex items-baseline gap-1.5 sm:gap-2 flex-wrap justify-end min-w-0">
              <span
                className="font-mono tabular-nums text-xs font-semibold text-fg truncate min-w-0"
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
