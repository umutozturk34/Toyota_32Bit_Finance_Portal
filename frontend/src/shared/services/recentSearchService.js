import api from './api';

const PATH = '/user/recent-searches';

export const recentSearchService = {
  list: async () => {
    const response = await api.get(PATH);
    return response.data.data;
  },

  record: async ({ code, type, name }) => {
    const response = await api.post(PATH, { code, type, name });
    return response.data.data;
  },

  clear: async () => {
    await api.delete(PATH);
  },

  remove: async ({ code, type }) => {
    const response = await api.delete(`${PATH}/${encodeURIComponent(type)}/${encodeURIComponent(code)}`);
    return response.data.data;
  },
};
