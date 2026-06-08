import { viopQuoteCurrency } from '../../../shared/utils/priceCurrency';

export const FRACTIONAL_TYPES = ['CRYPTO', 'FOREX', 'COMMODITY'];
export const ONE_HOUR_MS = 60 * 60 * 1000;

export function resolveNativeCurrency(target) {
  if (!target) return 'TRY';
  if (target.assetType === 'CRYPTO') {
    if ((target.assetCode || '').toLowerCase() === 'tether') return 'TRY';
    return 'USD';
  }
  // VIOP quote currency = symbol date-stripped suffix (not PARA_BIRIMI metadata).
  if (target.assetType === 'VIOP') {
    return viopQuoteCurrency(target.assetCode);
  }
  // Commodities are cross-converted to TRY at ingest, so prices are TRY.
  if (target.assetType === 'COMMODITY') {
    return 'TRY';
  }
  return 'TRY';
}
export const SUCCESS_HOLD_MS = 1100;
export const PROCESSING_STEP_DEFS = [
  { labelKey: 'validating', duration: 400 },
  { labelKey: 'checkingMarket', duration: 400 },
  { labelKey: 'updating', duration: 400 },
];

export function todayInputValue() {
  const now = new Date();
  const offset = now.getTimezoneOffset();
  return new Date(now.getTime() - offset * 60_000).toISOString().slice(0, 10);
}

export function isoToDateInput(iso) {
  if (!iso) return todayInputValue();
  return new Date(iso).toISOString().slice(0, 10);
}

export function dateInputToIso(value) {
  if (!value) return null;
  return new Date(`${value}T12:00:00`).toISOString();
}

export function buildInitialState(mode, asset, position) {
  if (mode === 'edit' && position) {
    return {
      entryDate: isoToDateInput(position.entryDate),
      entryPrice: String(position.entryPrice ?? ''),
      quantity: String(position.quantity ?? ''),
    };
  }
  return {
    entryDate: todayInputValue(),
    entryPrice: asset?.currentPrice ? String(asset.currentPrice) : '',
    quantity: '',
  };
}

export function resolveTarget(mode, asset, position) {
  if (mode === 'edit' && position) {
    return {
      assetType: position.assetType,
      assetCode: position.assetCode,
      assetName: position.assetName,
      assetImage: position.assetImage,
    };
  }
  return {
    assetType: asset?.type,
    assetCode: asset?.code,
    assetName: asset?.name,
    assetImage: asset?.image,
  };
}

export function toYearMonth(isoDate) {
  return isoDate ? isoDate.slice(0, 7) : new Date().toISOString().slice(0, 7);
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

function collectIsoDateEntries(node, out) {
  if (!node || typeof node !== 'object') return;
  for (const [key, value] of Object.entries(node)) {
    if (ISO_DATE.test(key) && (typeof value === 'number' || typeof value === 'string')) {
      out.set(key, Number(value));
    } else if (typeof value === 'object') {
      collectIsoDateEntries(value, out);
    }
  }
}

export function buildPriceIndex(response) {
  const index = new Map();
  collectIsoDateEntries(response, index);
  return index;
}

/**
 * Price for {@code dateIso}, falling back to the most recent earlier date in the index when that exact day
 * has none — so a non-trading day (weekend/holiday) suggests the last active value instead of nothing.
 * ISO {@code YYYY-MM-DD} keys sort lexically, so a string compare finds the latest day on or before.
 */
export function latestPriceAtOrBefore(priceIndex, dateIso) {
  if (!priceIndex || !dateIso) return undefined;
  const exact = priceIndex.get(dateIso);
  if (exact != null) return exact;
  let bestKey = null;
  for (const key of priceIndex.keys()) {
    if (key <= dateIso && (bestKey === null || key > bestKey)) bestKey = key;
  }
  return bestKey === null ? undefined : priceIndex.get(bestKey);
}

export function preventDecimal(e) {
  if (e.key === '.' || e.key === ',') e.preventDefault();
}

export function describeAction(t, isEdit, form, displayCode, isFractional) {
  const qty = isFractional ? form.quantity : Math.floor(Number(form.quantity || 0));
  return isEdit
    ? t('positionForm.success.subtitleEdit', { code: displayCode })
    : t('positionForm.success.subtitleAdd', { code: displayCode, qty });
}
