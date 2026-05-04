import api from './api';

const PATH = '/watchlists';

export const watchlistService = {
  list: async () => {
    const response = await api.get(PATH);
    return response.data.data;
  },

  create: async (name) => {
    const response = await api.post(PATH, { name });
    return response.data.data;
  },

  rename: async (id, name) => {
    const response = await api.patch(`${PATH}/${id}`, { name });
    return response.data.data;
  },

  remove: async (id) => {
    await api.delete(`${PATH}/${id}`);
  },

  listItems: async (id) => {
    const response = await api.get(`${PATH}/${id}/items`);
    return response.data.data;
  },

  addItem: async (id, payload) => {
    const response = await api.post(`${PATH}/${id}/items`, payload);
    return response.data.data;
  },

  removeItem: async (itemId) => {
    await api.delete(`${PATH}/items/${itemId}`);
  },

  addToFavorites: async (payload) => {
    const response = await api.post(`${PATH}/favorites/items`, payload);
    return response.data.data;
  },
};
