const CATEGORY_TO_MACRO_TYPE = {
  INFLATION: 'MACRO_INFLATION',
  RATES: 'MACRO_RATE',
  DEPOSIT: 'MACRO_DEPOSIT',
};

const SYNONYMS = {
  tr: {
    'enflasyon': { category: 'INFLATION' },
    'tüfe': { expandedQuery: 'TÜFE' },
    'tufe': { expandedQuery: 'TÜFE' },
    'üfe': { expandedQuery: 'ÜFE' },
    'ufe': { expandedQuery: 'ÜFE' },
    'cpi': { category: 'INFLATION' },
    'faiz': { category: 'RATES' },
    'faizler': { category: 'RATES' },
    'politika faizi': { expandedQuery: 'TLREF' },
    'tlref': { expandedQuery: 'TLREF' },
    'gecelik faiz': { expandedQuery: 'TLREF' },
    'tcmb': { category: 'RATES' },
    'merkez bankası': { category: 'RATES' },
    'mevduat': { category: 'DEPOSIT' },
    'mevduat faizi': { category: 'DEPOSIT' },
    'dolar': { expandedQuery: 'USD' },
    'amerikan doları': { expandedQuery: 'USD' },
    'usd/try': { expandedQuery: 'USD' },
    'euro': { expandedQuery: 'EUR' },
    'avro': { expandedQuery: 'EUR' },
    'sterlin': { expandedQuery: 'GBP' },
    'pound': { expandedQuery: 'GBP' },
    'altın': { expandedQuery: 'XAU' },
    'gram altın': { expandedQuery: 'XAU' },
    'ons altın': { expandedQuery: 'XAU' },
    'gold': { expandedQuery: 'XAU' },
    'gümüş': { expandedQuery: 'XAG' },
    'silver': { expandedQuery: 'XAG' },
    'platin': { expandedQuery: 'XPT' },
    'paladyum': { expandedQuery: 'XPD' },
    'petrol': { expandedQuery: 'OIL' },
    'brent': { expandedQuery: 'BRENT' },
    'doğalgaz': { expandedQuery: 'NATGAS' },
    'bakır': { expandedQuery: 'COPPER' },
    'bist': { type: 'STOCK', expandedQuery: 'BIST' },
    'bist 100': { type: 'STOCK', expandedQuery: 'XU100' },
    'borsa': { type: 'STOCK' },
    'borsa istanbul': { type: 'STOCK' },
    'hisse': { type: 'STOCK' },
    'hisseler': { type: 'STOCK' },
    'kripto': { type: 'CRYPTO' },
    'kripto para': { type: 'CRYPTO' },
    'bitcoin': { expandedQuery: 'BTC' },
    'btc': { expandedQuery: 'BTC' },
    'ethereum': { expandedQuery: 'ETH' },
    'eth': { expandedQuery: 'ETH' },
    'fon': { type: 'FUND' },
    'yatırım fonu': { type: 'FUND' },
    'tefas': { type: 'FUND' },
    'emtia': { type: 'COMMODITY' },
    'döviz': { type: 'FOREX' },
    'kur': { type: 'FOREX' },
    'forex': { type: 'FOREX' },
    'bono': { type: 'BOND' },
    'tahvil': { type: 'BOND' },
    'devlet tahvili': { type: 'BOND' },
    'viop': { type: 'VIOP' },
    'türev': { type: 'VIOP' },
    'türev ürün': { type: 'VIOP' },
    'futures': { type: 'VIOP' },
    'vadeli işlem': { type: 'VIOP' },
    'opsiyon': { type: 'VIOP' },
  },
  en: {
    'inflation': { category: 'INFLATION' },
    'cpi': { category: 'INFLATION' },
    'tufe': { expandedQuery: 'TÜFE' },
    'tüfe': { expandedQuery: 'TÜFE' },
    'ppi': { expandedQuery: 'ÜFE' },
    'rate': { category: 'RATES' },
    'rates': { category: 'RATES' },
    'interest rate': { category: 'RATES' },
    'policy rate': { expandedQuery: 'TLREF' },
    'tlref': { expandedQuery: 'TLREF' },
    'overnight rate': { expandedQuery: 'TLREF' },
    'tcmb': { category: 'RATES' },
    'central bank': { category: 'RATES' },
    'deposit': { category: 'DEPOSIT' },
    'deposit rate': { category: 'DEPOSIT' },
    'savings rate': { category: 'DEPOSIT' },
    'dollar': { expandedQuery: 'USD' },
    'usd': { expandedQuery: 'USD' },
    'us dollar': { expandedQuery: 'USD' },
    'euro': { expandedQuery: 'EUR' },
    'eur': { expandedQuery: 'EUR' },
    'pound': { expandedQuery: 'GBP' },
    'sterling': { expandedQuery: 'GBP' },
    'gbp': { expandedQuery: 'GBP' },
    'gold': { expandedQuery: 'XAU' },
    'silver': { expandedQuery: 'XAG' },
    'platinum': { expandedQuery: 'XPT' },
    'palladium': { expandedQuery: 'XPD' },
    'oil': { expandedQuery: 'OIL' },
    'brent': { expandedQuery: 'BRENT' },
    'natural gas': { expandedQuery: 'NATGAS' },
    'copper': { expandedQuery: 'COPPER' },
    'stock': { type: 'STOCK' },
    'stocks': { type: 'STOCK' },
    'shares': { type: 'STOCK' },
    'equity': { type: 'STOCK' },
    'bist': { type: 'STOCK', expandedQuery: 'BIST' },
    'crypto': { type: 'CRYPTO' },
    'cryptocurrency': { type: 'CRYPTO' },
    'bitcoin': { expandedQuery: 'BTC' },
    'btc': { expandedQuery: 'BTC' },
    'ethereum': { expandedQuery: 'ETH' },
    'eth': { expandedQuery: 'ETH' },
    'fund': { type: 'FUND' },
    'funds': { type: 'FUND' },
    'commodity': { type: 'COMMODITY' },
    'commodities': { type: 'COMMODITY' },
    'forex': { type: 'FOREX' },
    'currency': { type: 'FOREX' },
    'fx': { type: 'FOREX' },
    'bond': { type: 'BOND' },
    'bonds': { type: 'BOND' },
    'government bond': { type: 'BOND' },
    'viop': { type: 'VIOP' },
    'derivative': { type: 'VIOP' },
    'derivatives': { type: 'VIOP' },
    'futures': { type: 'VIOP' },
    'option': { type: 'VIOP' },
    'options': { type: 'VIOP' },
  },
};

function normalize(input) {
  return (input ?? '').trim().toLowerCase();
}

function resolveInLocale(query, locale) {
  const map = SYNONYMS[locale];
  if (!map) return null;
  const q = normalize(query);
  if (!q) return null;
  if (map[q]) return map[q];
  if (q.length < 3) return null;
  for (const key of Object.keys(map)) {
    if (key.startsWith(q)) return map[key];
  }
  return null;
}

export function resolveSynonym(query, locale) {
  const primary = (locale ?? 'en').toLowerCase().startsWith('tr') ? 'tr' : 'en';
  const fallback = primary === 'tr' ? 'en' : 'tr';
  return resolveInLocale(query, primary) ?? resolveInLocale(query, fallback);
}

export function categoryToMacroType(category) {
  return CATEGORY_TO_MACRO_TYPE[category] ?? null;
}
