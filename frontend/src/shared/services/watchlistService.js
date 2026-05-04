import api from './api';

const PATH = '/watchlist';

export const watchlistService = {
  list: async () => {
    const response = await api.get(PATH);
    return response.data.data;
  },

  add: async (payload) => {
    const response = await api.post(PATH, payload);
    return response.data.data;
  },

  remove: async (id) => {
    await api.delete(`${PATH}/${id}`);
  },
};
