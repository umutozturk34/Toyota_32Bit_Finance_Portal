import api from './api';

const PATH = '/market-status';

export const marketStatusService = {
  list: async () => {
    const response = await api.get(PATH);
    return response.data.data ?? [];
  },
};
