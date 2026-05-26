import api from '../../../shared/services/api';

export const bondService = {
  getAllBonds: async (params = {}) => {
    const response = await api.get('/bonds', { params });
    return response.data.data;
  },

  getBondByCode: async (seriesCode) => {
    try {
      const response = await api.get(`/bonds/${seriesCode}`);
      return response.data.data;
    } catch (error) {
      if (error.response?.status === 404) return null;
      throw error;
    }
  },

  getRateHistory: async (isinCode, period) => {
    const params = period ? { period } : {};
    const response = await api.get(`/bonds/rate-history/${isinCode}`, { params });
    return response.data.data;
  },

  getTypes: async () => {
    const response = await api.get('/bonds/types');
    return response.data.data;
  },
};
