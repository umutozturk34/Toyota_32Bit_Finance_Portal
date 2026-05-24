import api from './api';

const ASSETS = '/market';

const PERIOD_MAP = {
  '1W': 'ONE_WEEK',
  '1M': 'ONE_MONTH',
  '3Y': 'THREE_YEARS',
  '3M': 'THREE_MONTHS',
  '6M': 'SIX_MONTHS',
  '1Y': 'ONE_YEAR',
  '5Y': 'FIVE_YEARS',
  'MAX': 'ALL',
  'ALL': 'ALL',
};

export const unifiedMarketService = {
  search: async (params = {}) => {
    const response = await api.get(ASSETS, { params });
    return response.data.data;
  },

  getByCode: async (type, code) => {
    try {
      const response = await api.get(ASSETS, { params: { type, code } });
      const paged = response.data.data;
      return paged.content?.[0] || null;
    } catch (error) {
      if (error.response?.status === 404) return null;
      throw error;
    }
  },

  getOverview: async (limit) => {
    const response = await api.get(`${ASSETS}/overview`, { params: { limit } });
    return response.data.data;
  },

  getHistory: async (type, code, period = 'ALL') => {
    const response = await api.get(`${ASSETS}/history`, {
      params: { type, code, period: PERIOD_MAP[period] || 'ALL' },
    });
    return response.data.data;
  },

  getMonthlyAvailability: async (type, code, yearMonth) => {
    const response = await api.get(`${ASSETS}/availability`, {
      params: { type, code, yearMonth },
    });
    return response.data?.data ?? response.data;
  },
};
