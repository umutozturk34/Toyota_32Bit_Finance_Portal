const CURRENCY_COUNTRY = {
  USD: 'US', EUR: 'EU', GBP: 'GB', CHF: 'CH', CAD: 'CA', AUD: 'AU', JPY: 'JP', SAR: 'SA',
  CNY: 'CN', DKK: 'DK', SEK: 'SE', NOK: 'NO', RUB: 'RU', QAR: 'QA', KWD: 'KW', AED: 'AE',
  ZAR: 'ZA', HKD: 'HK', PLN: 'PL', RON: 'RO', SGD: 'SG', NZD: 'NZ', CZK: 'CZ', HUF: 'HU',
  INR: 'IN', THB: 'TH', MXN: 'MX', BRL: 'BR',
};

export function flagEmoji(currencyCode) {
  const cc = CURRENCY_COUNTRY[currencyCode];
  if (!cc) return null;
  if (cc === 'EU') return '🇪🇺';
  return String.fromCodePoint(...cc.split('').map(c => 0x1F1E6 + c.charCodeAt(0) - 'A'.charCodeAt(0)));
}
