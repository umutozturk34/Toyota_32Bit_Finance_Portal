import api from './api';

const PATH = '/user/profile';

export const userProfileService = {
  get: async () => {
    const response = await api.get(PATH);
    return response.data.data;
  },

  update: async (payload) => {
    const response = await api.put(PATH, payload);
    return response.data.data;
  },
};
