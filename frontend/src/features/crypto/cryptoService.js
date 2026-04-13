import { unifiedMarketService } from '../../shared/services/unifiedMarketService';

export const cryptoService = {
  getAll: async (params = {}) => {
    return unifiedMarketService.search({ type: 'CRYPTO', ...params });
  },

  getById: async (id) => {
    return unifiedMarketService.getByCode('CRYPTO', id);
  },

  getHistory: async (id, period = 'ALL') => {
    return unifiedMarketService.getHistory('CRYPTO', id, period);
  },
};
