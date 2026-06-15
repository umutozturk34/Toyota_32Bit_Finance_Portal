import { resolveNativeCurrency } from './positionFormHelpers';

export const SORT_OPTION_IDS = ['currentValue', 'profitPercent', 'profitAmount', 'entryDate', 'assetCode', 'quantity'];

export const TYPE_ROUTES = { STOCK: '/stocks', CRYPTO: '/crypto', FOREX: '/forex', FUND: '/funds', COMMODITY: '/commodities', VIOP: '/viop' };

export const marketHref = (type, code) => `${TYPE_ROUTES[type] ?? '/market'}/${encodeURIComponent(code)}`;

export function formatEntryDate(dateStr, localeTag) {
  if (!dateStr) return '';
  return new Date(dateStr).toLocaleDateString(localeTag, { day: '2-digit', month: 'short', year: '2-digit' });
}

/**
 * The ONE canonical display-currency cost/P&L for a spot or VIOP position — so every surface (the positions row,
 * the P&L-by-type breakdown, …) agrees to the cent instead of each re-deriving it and drifting.
 *
 * In a non-TRY display frame the P&L is recomputed as {@code value@(exit|today) − cost@entry}, each leg converted
 * at its OWN date's FX (true per-date FX). It is NEVER the net TRY P&L converted at a single rate — that divides a
 * (proceeds@exitFX − cost@entryFX) figure by one FX, leaving an entry-vs-exit FX artifact (e.g. a USD crypto sold a
 * day later showed a few hundred dollars of phantom P&L). VIOP SHORT is direction-aware (it profits as its notional
 * falls). In the TRY frame the backend figures are already correct, so {@code costFrame/framePnl} are left null and
 * callers fall back to {@code entryValueTry / pnlTry}.
 *
 * @param pos  a position row: entryValueTry, marketValueTry, pnlTry, entryDate, exitDate, assetType, assetCode, …
 * @param money a useMoney() instance — only {@code convert} and {@code resolveTarget} are used
 * @returns {{ frameCcy, isNonTryFrame, costFrame, valueFrame, framePnl, entryValueTry }}
 */
export function positionFrame(pos, { convert, resolveTarget }) {
  const nativeCurrency = resolveNativeCurrency({ assetType: pos.assetType, assetCode: pos.assetCode });
  const frameCcy = resolveTarget('TRY', nativeCurrency);
  const isNonTryFrame = frameCcy !== 'TRY';
  const isDerivative = pos.assetType === 'VIOP';
  const isClosed = isDerivative
    ? !!(pos.assetName && String(pos.assetName).includes('KAPALI'))
    : !!pos.exitDate;
  // The backend entry value verbatim; deriving it as marketValue − pnlTry is wrong for a VIOP SHORT whose
  // direction-aware pnl ≠ value − cost (kept only as a fallback when the field is absent).
  const entryValueTry = pos.entryValueTry != null
    ? Number(pos.entryValueTry)
    : Number(pos.marketValueTry) - Number(pos.pnlTry);

  if (!isNonTryFrame) {
    return { frameCcy, isNonTryFrame, costFrame: null, valueFrame: null, framePnl: null, entryValueTry };
  }

  const costFrame = convert(entryValueTry, 'TRY', nativeCurrency, pos.entryDate);
  const valueFrame = convert(pos.marketValueTry, 'TRY', nativeCurrency, isClosed ? pos.exitDate : undefined);
  const isShortDerivative = isDerivative
    && (pos.derivative?.direction || String(pos.assetName || '').split(' · ')[0]) === 'SHORT';
  const directionSign = isShortDerivative ? -1 : 1;
  const framePnl = costFrame != null && valueFrame != null ? directionSign * (valueFrame - costFrame) : null;
  return { frameCcy, isNonTryFrame, costFrame, valueFrame, framePnl, entryValueTry };
}
