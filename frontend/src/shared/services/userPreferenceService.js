import api from './api';

const PREFERENCES_PATH = '/user/preferences';

export const userPreferenceService = {
  get: async () => {
    const response = await api.get(PREFERENCES_PATH);
    return response.data.data;
  },

  update: async (partial) => {
    const response = await api.put(PREFERENCES_PATH, partial);
    return response.data.data;
  },
};
