import api from '../../shared/services/api';

export const cryptoService = {
  getById: async (id) => {
    try {
      const response = await api.get(`/market/${id}`);
      return response.data.data;
    } catch (error) {
      if (error.response?.status === 404) return null;
      throw error;
    }
  },

  getHistory: async (id) => {
    const response = await api.get(`/market/${id}/history`);
    return response.data.data;
  },

  getAll: async () => {
    const response = await api.get('/market');
    return response.data.data;
  },
};
