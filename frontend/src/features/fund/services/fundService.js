import api from '../../../shared/services/api';
import { createMarketService } from '../../../shared/services/createMarketService';

const baseService = createMarketService('FUND');

export const fundService = {
  ...baseService,
  getSubCategories: async () => {
    const response = await api.get('/markets/funds/sub-categories');
    return response.data?.data ?? [];
  },
};
