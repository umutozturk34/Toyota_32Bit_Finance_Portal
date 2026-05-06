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

  conversations: async ({ page = 0, size = 20 } = {}) => {
    const response = await api.get(`${ADMIN_PATH}/conversations`, { params: { page, size } });
    return response.data.data;
  },

  conversation: async (userSub) => {
    const response = await api.get(`${ADMIN_PATH}/conversations/${encodeURIComponent(userSub)}`);
    return response.data.data;
  },

  closeConversation: async (userSub) => {
    await api.post(`${ADMIN_PATH}/conversations/${encodeURIComponent(userSub)}/close`);
  },

  reopenConversation: async (userSub) => {
    await api.post(`${ADMIN_PATH}/conversations/${encodeURIComponent(userSub)}/reopen`);
  },

  deleteConversation: async (userSub) => {
    await api.delete(`${ADMIN_PATH}/conversations/${encodeURIComponent(userSub)}`);
  },

  broadcast: async ({ title, body }) => {
    const response = await api.post('/admin/notifications/broadcast', { title, body });
    return response.data.data;
  },
};
