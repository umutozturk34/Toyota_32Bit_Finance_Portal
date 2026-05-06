import api from './api';

const PATH = '/notification-preferences';

export const notificationPreferenceService = {
  get: async () => {
    const response = await api.get(PATH);
    return response.data.data;
  },

  update: async (partial) => {
    const response = await api.put(PATH, partial);
    return response.data.data;
  },
};
