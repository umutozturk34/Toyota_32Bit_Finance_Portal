import api from './api';
import { unifiedMarketService } from './unifiedMarketService';

export function createMarketService(type) {
  return {
    getAll: (params = {}) => unifiedMarketService.search({ type, ...params }),
    getByCode: (code) => unifiedMarketService.getByCode(type, code),
    getHistory: (code, period = 'ALL') => unifiedMarketService.getHistory(type, code, period),
    getGroupCounts: async () => {
      const res = await api.get('/market/group-counts', { params: { type } });
      return res.data.data;
    },
  };
}
