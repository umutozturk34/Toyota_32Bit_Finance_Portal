import api from '../../shared/services/api';

export const newsService = {
  search: async (params = {}) => {
    const response = await api.get('/news', { params });
    return response.data.data;
  },

  getNewsById: async (id) => {
    const response = await api.get(`/news/${id}`);
    return response.data.data || null;
  },

  getCategories: async () => {
    const response = await api.get('/news/categories');
    return response.data.data;
  },
};
