import api from '../../shared/services/api';

export const bondService = {
  getAllBonds: async () => {
    const response = await api.get('/bonds');
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

  getRateHistory: async (isinCode) => {
    const response = await api.get(`/bonds/rate-history/${isinCode}`);
    return response.data.data;
  },
};
