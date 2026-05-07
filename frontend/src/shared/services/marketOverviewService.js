import api from './api';

const OVERVIEW_PATH = '/market/overview';

export const marketOverviewService = {
  get: async () => {
    const response = await api.get(OVERVIEW_PATH);
    return response.data.data;
  },
};
