import api from '../../../shared/services/api';

const PATH = '/admin/users';

export const adminUserService = {
  list: async ({ first = 0, max = 50, search = '' } = {}) => {
    const params = { first, max };
    if (search) params.search = search;
    const response = await api.get(PATH, { params });
    return response.data.data;
  },

  count: async ({ search = '' } = {}) => {
    const params = {};
    if (search) params.search = search;
    const response = await api.get(`${PATH}/count`, { params });
    return response.data.data ?? 0;
  },

  ban: async (userId) => {
    await api.put(`${PATH}/${userId}/ban`);
  },

  unban: async (userId) => {
    await api.put(`${PATH}/${userId}/unban`);
  },
};
