import { useMemo } from 'react';
import { isMacro, isRateLike, nativeCurrencyFor } from '../lib/compareSeriesUtils';

// Currency-framing derivation for the compare page: from the selected series, the display-currency choice, and
// the link's pinned overrides, work out the single targetCurrency every series re-frames to, whether each stays
// in its own native (originalView), and the user-facing notices. Extracted verbatim from ComparePage so the
// page stays a thin shell; every useMemo body, dependency array, and explanatory comment is preserved exactly
// to keep behavior identical (the frontend has no unit tests to catch a subtle drift).
export default function useCompareFrame({
  selected,
  displayCurrency,
  useExplicitBounds,
  initialNominals,
  initialCurrency,
  macroListError,
  macroUnitByCode,
}) {
  // Beater click-through carries the table's authoritative (cached, backend-computed) nominal returns as
  // code:pct pairs, so CompareInfoBar prints the exact same % as the row clicked instead of the frontend
  // re-compound (which drifts ~0.5pt on the lead-in). Only while the pinned Beater window is active; once
  // the user changes range/selection (useExplicitBounds off) the recomputed % takes back over.
  const authoritativeReturns = useMemo(() => {
    if (!useExplicitBounds || !initialNominals) return null;
    const map = {};
    for (const pair of initialNominals.split(',')) {
      const sep = pair.lastIndexOf(':');
      if (sep < 0) continue;
      const code = pair.slice(0, sep);
      const n = Number(pair.slice(sep + 1));
      if (code && Number.isFinite(n)) map[code] = n;
    }
    return Object.keys(map).length > 0 ? map : null;
  }, [useExplicitBounds, initialNominals]);
  // Comparing against a USD/EUR deposit frames the whole chart in that deposit's currency
  // (single non-TRY deposit only; mixed/none → no override).
  const depositFrameCurrency = useMemo(() => {
    const set = new Set(
      selected
        .filter((s) => s.type === 'MACRO_DEPOSIT')
        .map((s) => nativeCurrencyFor(s.type, s.code))
        .filter((c) => c !== 'TRY'),
    );
    return set.size === 1 ? [...set][0] : null;
  }, [selected]);
  // CPI/PPI (TÜFE/ÜFE) are TRY price indices with no USD/EUR form, so a comparison that includes one is pinned
  // to TRY — even over an explicit currency pick — and every other series converts to TRY per-date (only then
  // is "did it beat inflation" a real read). Every OTHER instrument stays selectable: its own native in
  // Original, the picked currency otherwise. What matters is each thing's native.
  const forceTryFrame = useMemo(() => {
    if (initialCurrency) return false;
    return selected.some((s) => s.type === 'MACRO_INFLATION');
  }, [selected, initialCurrency]);
  // Mixed native currencies among the compared series. Every FX-convertible series has a meaningful native:
  // assets (a USD crypto, a TRY stock), deposits (USD/EUR/TRY) AND compounded reference rates (the TRY-native
  // policy rate / TLREF, which grow as a TRY index and convert per-date). Only the rate-like indices — CPI/PPI
  // and bond yields (isRateLike) — have no currency to reconcile, so they're excluded; portfolio counts as TRY.
  // When the convertible natives differ there is no shared money to read returns in, so the series must NOT stay
  // in "original" (a USD series left in USD plots its USD-local return on a TRY chart) — frame in TRY and
  // convert each per-date. This is why a USD crypto vs the TRY policy rate reads the SAME in Original and in
  // TRY: both reconcile to TRY (one money wins) instead of plotting a USD return next to a TRY return.
  const mixedNative = useMemo(() => {
    const set = new Set(
      selected
        .filter((s) => !isRateLike(s.type))
        .map((s) => (s.type === 'PORTFOLIO' ? 'TRY' : nativeCurrencyFor(s.type, s.code))),
    );
    return set.size > 1;
  }, [selected]);
  const targetCurrency = useMemo(() => {
    if (initialCurrency) return initialCurrency;
    // A TR price index (CPI/PPI) pins TRY even over an explicit currency — it has no USD/EUR form.
    if (forceTryFrame) return 'TRY';
    // Otherwise an explicit display-currency choice (TRY/USD/EUR) always wins; every convertible series
    // re-frames to it per-date. "Original" falls through to the native-based defaults below (a single foreign
    // deposit in its own currency; mixed natives in TRY; otherwise the sole shared native).
    if (displayCurrency !== 'ORIGINAL') return displayCurrency;
    if (depositFrameCurrency) return depositFrameCurrency;
    if (mixedNative) return 'TRY';
    const first = selected.find((s) => !isMacro(s.type) && s.type !== 'PORTFOLIO');
    return first ? nativeCurrencyFor(first.type, first.code) : 'TRY';
  }, [displayCurrency, selected, depositFrameCurrency, forceTryFrame, mixedNative, initialCurrency]);
  // "Original" view (each series in its own native) only when no explicit currency / deposit frame / forced TRY
  // frame is in effect AND every series shares one native (nothing to reconcile).
  const originalView = displayCurrency === 'ORIGINAL'
    && !initialCurrency
    && !forceTryFrame
    && !depositFrameCurrency
    && !mixedNative;
  // When Original mode reconciles series that live in different currencies into one frame (targetCurrency),
  // tell the user so the converted values aren't mistaken for native ones. Guarded by !originalView so it can
  // never claim a common basis that was not actually applied (each series staying in its own native).
  const currencyReconciledNotice = useMemo(() => {
    if (originalView || displayCurrency !== 'ORIGINAL' || initialCurrency) return false;
    return new Set(selected.map((s) => nativeCurrencyFor(s.type, s.code))).size > 1;
  }, [originalView, displayCurrency, selected, initialCurrency]);

  // The /macro-indicators list failed, so a selected MACRO_RATE with no unit carried on its selection (an
  // old/bookmarked link without the `units` param) can't be classified PERCENT-vs-INDEX and is held empty by
  // the safety-net gate rather than mis-plotted as a raw level. Surface a minimal notice so that series isn't
  // silently blank with no explanation — a held line is correct, but the user deserves to know why.
  const macroUnitLoadFailed = useMemo(
    () => macroListError && Object.keys(macroUnitByCode).length === 0
      && selected.some((s) => s.type === 'MACRO_RATE' && !s.unit),
    [macroListError, macroUnitByCode, selected],
  );

  return {
    authoritativeReturns,
    forceTryFrame,
    targetCurrency,
    originalView,
    currencyReconciledNotice,
    macroUnitLoadFailed,
  };
}
