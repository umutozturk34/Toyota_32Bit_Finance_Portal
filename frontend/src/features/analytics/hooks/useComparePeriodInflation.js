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
    // value in force at the baseline = last observation on-or-before it; fall back to the earliest obs.
    let first = sorted[0];
    for (const p of sorted) {
      if (p.date <= base) first = p; else break;
    }
    const last = sorted[sorted.length - 1];
    if (!first || !last || first.value === 0) return null;
    return ((last.value - first.value) / Math.abs(first.value)) * 100;
  }, [enabled, baselineDate, cpi]);
}
