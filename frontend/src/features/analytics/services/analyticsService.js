import api from '../../../shared/services/api';

const BASE = '/analytics';

export const analyticsService = {
  simulate: async ({ amount, startDate, endDate, instruments }) => {
    const res = await api.post(`${BASE}/scenarios`, { amount, startDate, endDate, instruments });
    return res.data.data;
  },

  inflationBeaters: async (period = '1Y', benchmark) => {
    const params = { period };
    if (benchmark) params.benchmark = benchmark;
    const res = await api.get(`${BASE}/inflation-beaters`, { params });
    return res.data.data;
  },
};
