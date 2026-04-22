import api from '../../shared/services/api';
import { unifiedMarketService } from '../../shared/services/unifiedMarketService';

export const commodityService = {
  getAllCommodities: async (params = {}) => {
    return unifiedMarketService.search({ type: 'COMMODITY', ...params });
  },

  getCommodityByCode: async (code) => {
    return unifiedMarketService.getByCode('COMMODITY', code);
  },

  getCommodityHistory: async (code, period = 'ALL') => {
    return unifiedMarketService.getHistory('COMMODITY', code, period);
  },

  getSegmentCounts: async () => {
    const res = await api.get('/market/group-counts', { params: { type: 'COMMODITY' } });
    return res.data.data;
  },
};
