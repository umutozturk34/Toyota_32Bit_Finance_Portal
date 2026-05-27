export const INSTRUMENT_TYPES = [
  { id: 'SPOT',      labelKey: 'instrumentSpot',      placeholder: 'THYAO.IS', accent: '#5E6AD2' },
  { id: 'FOREX',     labelKey: 'instrumentForex',     placeholder: 'USD',      accent: '#06b6d4' },
  { id: 'COMMODITY', labelKey: 'instrumentCommodity', placeholder: 'XAUTRY',   accent: '#f59e0b' },
  { id: 'CRYPTO',    labelKey: 'instrumentCrypto',    placeholder: 'bitcoin',  accent: '#f97316' },
  { id: 'FUND',      labelKey: 'instrumentFund',      placeholder: 'TI2',      accent: '#8b5cf6' },
  { id: 'VIOP',      labelKey: 'instrumentViop',      placeholder: '',         accent: '#ef4444' },
  { id: 'BOND',      labelKey: 'instrumentBond',      placeholder: 'TRTXXX',   accent: '#d946ef' },
  { id: 'MACRO',     labelKey: 'instrumentMacro',     placeholder: 'TP.FE...', accent: '#0ea5e9' },
  { id: 'DEPOSIT',   labelKey: 'instrumentDeposit',   placeholder: 'TP.TRYTAS.MT06', accent: '#10b981' },
];

export const SERIES_COLORS = [
  '#5E6AD2',
  '#10b981',
  '#f59e0b',
  '#06b6d4',
  '#ef4444',
  '#8b5cf6',
  '#f97316',
];

export const PRESET_INSTRUMENTS = [
  { type: 'DEPOSIT', code: 'TP.TRYTAS.MT06', name: 'TRY 3M Mevduat', labelKey: 'analytics.preset.depositTry3m' },
  { type: 'DEPOSIT', code: 'TP.USDTAS.MT06', name: 'USD 3M Mevduat', labelKey: 'analytics.preset.depositUsd3m' },
  { type: 'DEPOSIT', code: 'TP.EURTAS.MT06', name: 'EUR 3M Mevduat', labelKey: 'analytics.preset.depositEur3m' },
  { type: 'FOREX',   code: 'USD',            name: 'USD/TRY',         labelKey: 'analytics.preset.usdTry' },
  { type: 'FOREX',   code: 'EUR',            name: 'EUR/TRY',         labelKey: 'analytics.preset.eurTry' },
  { type: 'COMMODITY', code: 'XAUTRY',       name: 'Altın',           labelKey: 'analytics.preset.gold' },
  { type: 'COMMODITY', code: 'XAGTRY',       name: 'Gümüş',           labelKey: 'analytics.preset.silver' },
  { type: 'CRYPTO',  code: 'bitcoin',        name: 'Bitcoin' },
  { type: 'CRYPTO',  code: 'ethereum',       name: 'Ethereum' },
  { type: 'SPOT',    code: 'THYAO.IS',       name: 'THYAO' },
  { type: 'SPOT',    code: 'GARAN.IS',       name: 'GARAN' },
  { type: 'SPOT',    code: 'ASELS.IS',       name: 'ASELS' },
];

export const PERIODS = [
  { id: '1M', labelKey: 'periodOneMonth' },
  { id: '3M', labelKey: 'periodThreeMonths' },
  { id: '6M', labelKey: 'periodSixMonths' },
  { id: '1Y', labelKey: 'periodOneYear' },
  { id: '3Y', labelKey: 'periodThreeYears' },
  { id: '5Y', labelKey: 'periodFiveYears' },
];
