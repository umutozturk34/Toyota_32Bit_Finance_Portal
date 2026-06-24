import api from '../../../shared/services/api';

const BASE = '/analytics';

export const analyticsService = {
  simulate: async ({ amount, startDate, endDate, instruments, targetCurrency }) => {
    const body = { amount, startDate, endDate, instruments };
    if (targetCurrency) body.targetCurrency = targetCurrency;
    const res = await api.post(`${BASE}/scenarios`, body);
    return res.data.data;
  },

  inflationBeaters: async (period = '1Y', benchmark, targetCurrency) => {
    const params = { period };
    if (benchmark) params.benchmark = benchmark;
    if (targetCurrency) params.targetCurrency = targetCurrency;
    const res = await api.get(`${BASE}/inflation-beaters`, { params });
    return res.data.data;
  },

  // Realized TRY returns for every spot asset across all windows — TRY-only (the display currency is
  // irrelevant), computed and cached server-side. No params: one dataset, the client filters/ranks it.
  assetReturns: async () => {
    const res = await api.get(`${BASE}/returns`);
    return res.data.data;
  },

  portfolioSeries: async (portfolioId, { from, to }, mode = 'pnl') => {
    // mode 'twr' → the time-weighted-return index (the compare % line; contribution-neutral, so it answers
    // "did my holdings beat the benchmark/inflation over this window" — comparable to a generic CPI line);
    // 'pnl' → cumulative profit/loss in TRY (the Kâr/Zarar Total amount, surfaced alongside the % so the
    // money is still visible). Compare fetches twr+pnl together.
    const params = { from, to };
    if (mode === 'twr') params.twr = true;
    else if (mode === 'pnl') params.pnl = true;
    const res = await api.get(`${BASE}/portfolio-series/${portfolioId}`, { params });
    return res.data.data;
  },
};
