const CURRENCY_SYMBOLS = { TRY: '₺', USD: '$', EUR: '€' };

// Quote currency a VIOP price is denominated in, derived from the symbol (mirrors backend
// Currency.viopQuoteCurrencyOf). Options (O_ prefix) are always TRY. Futures (F_ prefix) carry a
// trailing expiry (MMYY); the quote currency is the last pair token before it. e.g. F_USDTRY0626 ->
// TRY (price is TRY per USD), F_EURUSD0626 -> USD. The exchange PARA_BIRIMI (metadata.currency) is
// not the quote currency, so it must not be used here.
export function viopQuoteCurrency(code) {
  if (!code) return 'TRY';
  const upper = code.toUpperCase();
  if (upper.startsWith('O_')) return 'TRY';
  const base = upper.replace(/\d{4}$/, '');
  if (base.endsWith('USD')) return 'USD';
  if (base.endsWith('EUR')) return 'EUR';
  return 'TRY';
}

export function priceCurrencyOf(asset) {
  if (!asset) return 'TRY';
  // Watchlist DTOs key the asset as assetCode/marketType; search/market DTOs use code/type.
  const type = asset.type ?? asset.marketType;
  const code = asset.code ?? asset.assetCode;
  // An explicit backend-resolved quote currency wins over symbol-derived inference.
  if (asset.currency) return asset.currency;
  if (type === 'VIOP') return viopQuoteCurrency(code);
  return 'TRY';
}

export function currencySymbolOf(currency) {
  return CURRENCY_SYMBOLS[currency] || currency || '';
}
