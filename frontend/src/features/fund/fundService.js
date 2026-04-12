import api from '../../shared/services/api';
import { unifiedMarketService } from '../../shared/services/unifiedMarketService';

export const fundService = {
  getAllFunds: async (params = {}) => {
    return unifiedMarketService.search({ type: 'FUND', ...params });
  },

  getFundByCode: async (code) => {
    return unifiedMarketService.getByCode('FUND', code);
  },

  getFundHistory: async (code, period = 'ALL') => {
    return unifiedMarketService.getHistory('FUND', code, period);
  },

  getTypes: async () => {
    const response = await api.get('/market/group-counts', { params: { type: 'FUND' } });
    return response.data.data;
  },
};
