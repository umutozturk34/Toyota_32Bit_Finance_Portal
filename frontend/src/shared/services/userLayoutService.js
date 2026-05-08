import api from './api';

const LAYOUT_PATH = '/user/layout';

export const userLayoutService = {
  get: async () => {
    const response = await api.get(LAYOUT_PATH);
    return response.data.data;
  },

  saveOverview: async (overview) => {
    const response = await api.put(`${LAYOUT_PATH}/overview`, overview);
    return response.data.data;
  },
};
