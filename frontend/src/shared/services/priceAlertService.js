import api from './api';

const PATH = '/price-alerts';

export const priceAlertService = {
  list: async ({ page = 0, size = 20 } = {}) => {
    const response = await api.get(PATH, { params: { page, size } });
    return response.data.data;
  },

  create: async (payload) => {
    const response = await api.post(PATH, payload);
    return response.data.data;
  },

  remove: async (id) => {
    await api.delete(`${PATH}/${id}`);
  },

  reactivate: async (id) => {
    const response = await api.post(`${PATH}/${id}/reactivate`);
    return response.data.data;
  },

  update: async (id, payload) => {
    const response = await api.put(`${PATH}/${id}`, payload);
    return response.data.data;
  },
};
