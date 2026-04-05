import api from '../../shared/services/api';

export const forexService = {
  getForexByCode: async (currencyCode) => {
    try {
      const response = await api.get(`/forex/${currencyCode}`);
      return response.data.data;
    } catch (error) {
      if (error.response?.status === 404) return null;
      throw error;
    }
  },

  getAllForex: async () => {
    const response = await api.get('/forex');
    return response.data.data;
  },

  getForexHistory: async (currencyCode) => {
    const response = await api.get(`/forex/${currencyCode}/history`);
    return response.data.data;
  },
};
