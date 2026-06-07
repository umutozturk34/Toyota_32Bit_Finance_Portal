export const SORT_OPTION_IDS = ['currentValue', 'profitPercent', 'profitAmount', 'entryDate', 'assetCode', 'quantity'];

export const TYPE_ROUTES = { STOCK: '/stocks', CRYPTO: '/crypto', FOREX: '/forex', FUND: '/funds', COMMODITY: '/commodities', VIOP: '/viop' };

export const marketHref = (type, code) => `${TYPE_ROUTES[type] ?? '/market'}/${encodeURIComponent(code)}`;

export function formatEntryDate(dateStr, localeTag) {
  if (!dateStr) return '';
  return new Date(dateStr).toLocaleDateString(localeTag, { day: '2-digit', month: 'short', year: '2-digit' });
}
