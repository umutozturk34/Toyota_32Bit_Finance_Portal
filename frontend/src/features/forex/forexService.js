import { unifiedMarketService } from '../../shared/services/unifiedMarketService';

export const forexService = {
  getAllForex: async (params = {}) => {
    return unifiedMarketService.search({ type: 'FOREX', ...params });
  },

  getForexByCode: async (code) => {
    return unifiedMarketService.getByCode('FOREX', code);
  },

  getForexHistory: async (code, period = 'ALL') => {
    return unifiedMarketService.getHistory('FOREX', code, period);
  },
};
