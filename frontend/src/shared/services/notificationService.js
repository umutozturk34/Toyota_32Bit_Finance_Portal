import api from './api';

const PATH = '/notifications';

export const notificationService = {
  list: async ({ unreadOnly = false, page = 0, size = 20 } = {}) => {
    const response = await api.get(PATH, { params: { unreadOnly, page, size } });
    return response.data.data;
  },

  unreadCount: async () => {
    const response = await api.get(`${PATH}/unread-count`);
    return response.data.data;
  },

  markRead: async (id) => {
    const response = await api.patch(`${PATH}/${id}/read`);
    return response.data.data;
  },

  markAllRead: async () => {
    const response = await api.post(`${PATH}/read-all`);
    return response.data.data;
  },

  remove: async (id) => {
    await api.delete(`${PATH}/${id}`);
  },
};
