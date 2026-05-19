const CURRENCY_SYMBOLS = { TRY: '₺', USD: '$', EUR: '€' };

export function priceCurrencyOf(asset) {
  if (!asset) return 'TRY';
  if (asset.currency) return asset.currency;
  const type = asset.type ?? asset.marketType;
  if (type === 'VIOP') return asset.metadata?.currency || 'TRY';
  return 'TRY';
}

export function currencySymbolOf(currency) {
  return CURRENCY_SYMBOLS[currency] || currency || '';
}
