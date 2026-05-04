import api from './api';

const USER_PATH = '/messages';
const ADMIN_PATH = '/admin/messages';

export const messageService = {
  send: async (body) => {
    const response = await api.post(USER_PATH, { body });
    return response.data.data;
  },

  inbox: async ({ page = 0, size = 20 } = {}) => {
    const response = await api.get(`${USER_PATH}/inbox`, { params: { page, size } });
    return response.data.data;
  },

  sent: async ({ page = 0, size = 20 } = {}) => {
    const response = await api.get(`${USER_PATH}/sent`, { params: { page, size } });
    return response.data.data;
  },

  unreadCount: async () => {
    const response = await api.get(`${USER_PATH}/unread-count`);
    return response.data.data;
  },

  markRead: async (id) => {
    await api.patch(`${USER_PATH}/${id}/read`);
  },
};

export const adminMessageService = {
  send: async ({ recipientSub, body }) => {
    const response = await api.post(ADMIN_PATH, { recipientSub, body });
    return response.data.data;
  },

  inbox: async ({ page = 0, size = 20 } = {}) => {
    const response = await api.get(`${ADMIN_PATH}/inbox`, { params: { page, size } });
    return response.data.data;
  },

  inboxCount: async () => {
    const response = await api.get(`${ADMIN_PATH}/inbox-count`);
    return response.data.data;
  },
};
