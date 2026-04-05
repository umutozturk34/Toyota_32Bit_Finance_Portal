import api from '../../shared/services/api';

export const fundService = {
  getAllFunds: async () => {
    const response = await api.get('/funds');
    return response.data.data;
  },

  getFundByCode: async (fundCode) => {
    try {
      const response = await api.get(`/funds/${fundCode}`);
      return response.data.data;
    } catch (error) {
      if (error.response?.status === 404) return null;
      throw error;
    }
  },

  getFundHistory: async (fundCode) => {
    const response = await api.get(`/funds/${fundCode}/history`);
    return response.data.data;
  },
};
