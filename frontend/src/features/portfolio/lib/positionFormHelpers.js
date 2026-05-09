import { formatPriceTRY } from '../../../shared/utils/formatters';

export const FRACTIONAL_TYPES = ['CRYPTO', 'FOREX', 'COMMODITY'];
export const ONE_HOUR_MS = 60 * 60 * 1000;
export const SUCCESS_HOLD_MS = 1100;
export const PROCESSING_STEPS = [
  { label: 'İşlem doğrulanıyor...', duration: 400 },
  { label: 'Piyasa verisi kontrol ediliyor...', duration: 400 },
  { label: 'Portföy güncelleniyor...', duration: 400 },
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

const compactCurrencyFormatter = new Intl.NumberFormat('tr-TR', {
  notation: 'compact',
  style: 'currency',
  currency: 'TRY',
  maximumFractionDigits: 2,
});

export function formatTotalCost(cost) {
  if (cost == null) return 'N/A';
  if (Math.abs(cost) >= 1_000_000_000) return compactCurrencyFormatter.format(cost);
  return formatPriceTRY(cost);
}

export function preventDecimal(e) {
  if (e.key === '.' || e.key === ',') e.preventDefault();
}

export function describeAction(isEdit, form, displayCode, isFractional) {
  const qty = isFractional ? form.quantity : Math.floor(Number(form.quantity || 0));
  return isEdit ? `${displayCode} pozisyonunu güncelle` : `${displayCode} pozisyonu (${qty} adet) ekle`;
}
