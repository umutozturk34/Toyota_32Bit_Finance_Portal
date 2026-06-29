import { useMemo } from 'react';
import { useMacroIndicators, useMacroIndicatorHistory } from '../../macro/hooks/useMacroIndicators';

// Period CPI (TÜFE) growth % over the compare window, fetched independently of the selected series so the
// verdict hero can ALWAYS state the period inflation — even when the user hasn't added a TÜFE line. Gated by
// `enabled` to a TRY frame only: TÜFE deflates the lira, so quoting it against a USD/EUR-framed return is
// meaningless (the caller passes enabled=false there and skips this read entirely). When a CPI line is already
// on the chart the caller reads it from seriesData instead and leaves this disabled, so there is no double read.
//
// CPI is published monthly, so the window endpoints take the observation IN FORCE (the last on-or-before the
// date) at the baseline and at the window end — the same step/forward-fill reading used everywhere the index
// acts as a deflator. The CPI code is resolved from the inflation-indicator list (label cpiIndex / GENENDEKS),
// never hard-coded, mirroring FixedIncomePnlChart.
export default function useComparePeriodInflation({ enabled, baselineDate, endDate }) {
  const { data: inflationIndicators = [] } = useMacroIndicators({ category: 'INFLATION' });
  const cpiCode = useMemo(() => {
    const list = Array.isArray(inflationIndicators) ? inflationIndicators : [];
    const cpi = list.find((i) => i.label === 'cpiIndex')
      || list.find((i) => i.code?.includes('GENENDEKS'))
      || list[0];
    return cpi?.code;
  }, [inflationIndicators]);
  // Fetch from ~70 days before the baseline so the monthly index always has an observation on-or-before the
  // window start for the in-force reading.
  const from = useMemo(() => {
    if (!baselineDate) return undefined;
    const d = new Date(`${String(baselineDate).slice(0, 10)}T00:00:00`);
    d.setDate(d.getDate() - 70);
    return d.toISOString().slice(0, 10);
  }, [baselineDate]);
  const { data: cpi = [] } = useMacroIndicatorHistory(enabled ? cpiCode : undefined, { from, to: endDate });
  return useMemo(() => {
    if (!enabled || !baselineDate || !Array.isArray(cpi) || cpi.length === 0) return null;
    const sorted = cpi
      .map((p) => ({ date: String(p.observedAt).slice(0, 10), value: Number(p.value) }))
      .filter((p) => p.date && Number.isFinite(p.value))
      .sort((a, b) => a.date.localeCompare(b.date));
    if (sorted.length === 0) return null;
    const base = String(baselineDate).slice(0, 10);
    const last = sorted[sorted.length - 1];
    // Anchor the baseline SYMMETRICALLY to the latest published reading minus the window span — not to the raw
    // calendar baseline. CPI lags ~1 month (a month's figure is published early the NEXT month), so `last` is the
    // latest PUBLISHED index. But picking `first` as "in force on the calendar baseline" pulls in a figure that
    // was not published yet on that past date (the DB now holds it), shrinking the span to ~11 months and
    // under-reporting a 1Y window by ~one month of inflation (the 30.6-vs-32.6 gap). Pulling `first` back to
    // last.date − window keeps both ends at the same publication recency, so a 1Y compare matches the YoY rate.
    const end = endDate ? String(endDate).slice(0, 10) : base;
    const spanMs = Math.max(
      0,
      new Date(`${end}T00:00:00`).getTime() - new Date(`${base}T00:00:00`).getTime(),
    );
    const targetMs = new Date(`${last.date}T00:00:00`).getTime() - spanMs;
    let first = sorted[0];
    let bestDiff = Number.POSITIVE_INFINITY;
    for (const p of sorted) {
      const diff = Math.abs(new Date(`${p.date}T00:00:00`).getTime() - targetMs);
      if (diff < bestDiff) {
        bestDiff = diff;
        first = p;
      }
    }
    if (!first || !last || first.value === 0) return null;
    return ((last.value - first.value) / Math.abs(first.value)) * 100;
  }, [enabled, baselineDate, endDate, cpi]);
}
