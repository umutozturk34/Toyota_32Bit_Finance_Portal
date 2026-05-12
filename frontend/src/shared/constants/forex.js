const FOREX_CODES = [
  'USD', 'EUR', 'GBP', 'JPY', 'CHF', 'CAD', 'AUD', 'SAR', 'KRW', 'SEK',
  'NOK', 'DKK', 'KWD', 'RON', 'RUB', 'CNY', 'PKR', 'QAR', 'AZN', 'AED',
  'KZT', 'BGN', 'XDR',
];

export const getBaseCurrency = (currencyCode) => currencyCode;

export const getForexPairs = () => FOREX_CODES;
