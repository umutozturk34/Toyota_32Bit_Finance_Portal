import api from './api';

const PATH = '/admin/notifications';

export const broadcastService = {
  send: async ({ title, body }) => {
    const response = await api.post(`${PATH}/broadcast`, { title, body });
    return response.data.data;
  },
};
