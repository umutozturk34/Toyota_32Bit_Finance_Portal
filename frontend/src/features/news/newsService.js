import api from '../../shared/services/api';

export const newsService = {
  getLatestNews: async () => {
    const response = await api.get('/news');
    return response.data.data || [];
  },

  getByCategory: async (category) => {
    const response = await api.get(`/news/category/${category}`);
    return response.data.data || [];
  },

  getNewsById: async (id) => {
    const response = await api.get(`/news/${id}`);
    return response.data.data || null;
  },
};
