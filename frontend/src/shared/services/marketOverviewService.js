import api from './api';

const OVERVIEW_PATH = '/market/overview';
const DEFINITIONS_PATH = '/market/overview/widget-definitions';

export const marketOverviewService = {
  get: async (pageId) => {
    const params = pageId ? { page: pageId } : {};
    const response = await api.get(OVERVIEW_PATH, { params });
    return response.data.data;
  },
  getDefinitions: async () => {
    const response = await api.get(DEFINITIONS_PATH);
    return response.data.data;
  },
};
