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

  portfolioSeries: async (portfolioId, { from, to }) => {
    const res = await api.get(`${BASE}/portfolio-series/${portfolioId}`, { params: { from, to } });
    return res.data.data;
  },
};
