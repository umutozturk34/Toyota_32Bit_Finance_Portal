export const CATEGORY_THEME = {
  RATES:     { accent: '#5E6AD2', glow: 'rgba(94,106,210,0.35)',  soft: 'rgba(94,106,210,0.10)' },
  INFLATION: { accent: '#f59e0b', glow: 'rgba(245,158,11,0.35)',  soft: 'rgba(245,158,11,0.10)' },
  DEPOSIT:   { accent: '#10b981', glow: 'rgba(16,185,129,0.35)',  soft: 'rgba(16,185,129,0.10)' },
};

export const FALLBACK_THEME = { accent: '#6366f1', glow: 'rgba(99,102,241,0.30)', soft: 'rgba(99,102,241,0.10)' };

export const RANGES = [
  { id: '1W',  days: 7,     labelKey: 'rangeOneWeek'   },
  { id: '1M',  days: 30,    labelKey: 'rangeOneMonth'  },
  { id: '6M',  days: 180,   labelKey: 'rangeSixMonths' },
  { id: '1Y',  days: 365,   labelKey: 'rangeOneYear'   },
  { id: '3Y',  days: 1095,  labelKey: 'rangeThreeYears'},
  { id: '5Y',  days: 1825,  labelKey: 'rangeFiveYears' },
  { id: 'ALL', days: 11000, labelKey: 'rangeAll'       },
];

export const SPARK_DAYS = 90;

export const PROMINENT_ORDER = [
  'policyRate', 'tlrefRate', 'cpiIndex', 'ppiIndex',
  'depositTryTotal', 'depositUsdTotal', 'depositEurTotal',
];

export const DEPOSIT_MATURITY_ORDER = ['M1', 'M3', 'M6', 'M12', 'M12_PLUS', 'TOTAL'];
export const DEPOSIT_CURRENCIES = ['TRY', 'USD', 'EUR'];
