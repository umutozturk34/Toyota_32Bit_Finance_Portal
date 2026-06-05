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

  portfolioSeries: async (portfolioId, { from, to }, mode = 'pnl') => {
    // mode 'twr' → contribution-immune time-weighted-return index (the compare % line, comparable to
    // inflation); 'pnl' → cumulative profit/loss in TRY (the Kâr/Zarar Total amount, surfaced alongside
    // the % so the money is still visible). Compare fetches both and zips them by date.
    const params = { from, to };
    if (mode === 'twr') params.twr = true;
    else if (mode === 'pnl') params.pnl = true;
    const res = await api.get(`${BASE}/portfolio-series/${portfolioId}`, { params });
    return res.data.data;
  },
};
