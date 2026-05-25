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

  remove: async (id) => {
    await api.delete(`${PATH}/${id}`);
  },

  listItems: async (id, { sort = 'CUSTOM', direction = 'ASC' } = {}) => {
    const response = await api.get(`${PATH}/${id}/items`, { params: { sort, direction } });
    return response.data.data;
  },

  addItem: async (id, payload) => {
    const response = await api.post(`${PATH}/${id}/items`, payload);
    return response.data.data;
  },

  reorder: async (id, itemIds) => {
    const response = await api.put(`${PATH}/${id}/items/reorder`, { itemIds });
    return response.data.data;
  },

  removeItem: async (itemId) => {
    await api.delete(`${PATH}/items/${itemId}`);
  },

  updateItem: async (itemId, payload) => {
    const response = await api.put(`${PATH}/items/${itemId}`, payload);
    return response.data.data;
  },

  addToFavorites: async (payload) => {
    const response = await api.post(`${PATH}/favorites/items`, payload);
    return response.data.data;
  },
};
