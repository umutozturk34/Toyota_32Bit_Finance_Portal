import api from './api';
import { unifiedMarketService } from './unifiedMarketService';

export const getCryptoHistory = async (id, period = 'ALL') => {
  return unifiedMarketService.getHistory('CRYPTO', id, period);
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
  getForexHistory: async (currencyCode, period = 'ALL') => {
    return unifiedMarketService.getHistory('FOREX', currencyCode, period);
  },
};

export const stockService = {
  getStockHistory: async (symbol, period = 'ALL') => {
    return unifiedMarketService.getHistory('STOCK', symbol, period);
  },
};

export const fundService = {
  getAllFunds: async () => {
    const result = await unifiedMarketService.search({ type: 'FUND', size: 100 });
    return result.content || [];
  },
  getFundHistory: async (code, period = 'ALL') => {
    return unifiedMarketService.getHistory('FUND', code, period);
  },
};
