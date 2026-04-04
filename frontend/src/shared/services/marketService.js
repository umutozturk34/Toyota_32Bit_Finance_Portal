import api from './api';

export const getCryptoHistory = async (id) => {
  const response = await api.get(`/market/${id}/history`);
  return response.data.data;
};

export const trackedAssetService = {
  getByType: async (type, includeDisabled = false) => {
    const response = await api.get('/tracked-assets', {
      params: { type, includeDisabled },
    });
    return response.data.data;
  },
  getOne: async (type, code) => {
    try {
      const response = await api.get(`/tracked-assets/${type}/${encodeURIComponent(code)}`);
      return response.data.data;
    } catch (error) {
      if (error.response?.status === 404) return null;
      throw error;
    }
  },
};

export const forexService = {
  getForexHistory: async (currencyCode) => {
    const response = await api.get(`/forex/${currencyCode}/history`);
    return response.data.data;
  },
};

export const stockService = {
  getStockHistory: async (symbol) => {
    const response = await api.get(`/stocks/${symbol}/history`);
    return response.data.data;
  },
};

export const fundService = {
  getAllFunds: async () => {
    const response = await api.get('/funds');
    return response.data.data;
  },
  getFundHistory: async (fundCode) => {
    const response = await api.get(`/funds/${fundCode}/history`);
    return response.data.data;
  },
};
