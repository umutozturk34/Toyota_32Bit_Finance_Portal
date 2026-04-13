import api from '../../shared/services/api';
import { unifiedMarketService } from '../../shared/services/unifiedMarketService';

export const stockService = {
  getAllStocks: async (params = {}) => {
    return unifiedMarketService.search({ type: 'STOCK', ...params });
  },

  getStockBySymbol: async (symbol) => {
    return unifiedMarketService.getByCode('STOCK', symbol);
  },

  getStockHistory: async (symbol, period = 'ALL') => {
    return unifiedMarketService.getHistory('STOCK', symbol, period);
  },

  getSegmentCounts: async () => {
    const res = await api.get('/market/group-counts', { params: { type: 'STOCK' } });
    return res.data.data;
  },
};
