import api from '../../shared/services/api';

export const stockService = {
  getStockBySymbol: async (symbol) => {
    try {
      const response = await api.get(`/stocks/${symbol}`);
      return response.data.data;
    } catch (error) {
      if (error.response?.status === 404) return null;
      throw error;
    }
  },

  getAllStocks: async () => {
    const response = await api.get('/stocks');
    return response.data.data;
  },

  getStockHistory: async (symbol) => {
    const response = await api.get(`/stocks/${symbol}/history`);
    return response.data.data;
  },
};
