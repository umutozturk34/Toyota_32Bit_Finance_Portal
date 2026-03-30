import api from './api';

export const newsService = {
    getLatestNews: async () => {
        try {
            const response = await api.get('/news');
            return response.data.data || [];
        } catch (error) {
            console.error('Error fetching latest news:', error);
            throw error;
        }
    },

    getNewsByCategory: async (category) => {
        try {
            const response = await api.get(`/news/category/${category}`);
            return response.data.data || [];
        } catch (error) {
            console.error(`Error fetching news for category ${category}:`, error);
            throw error;
        }
    },

    getNewsById: async (id) => {
        try {
            const response = await api.get(`/news/${id}`);
            return response.data.data || null;
        } catch (error) {
            console.error(`Error fetching news article ${id}:`, error);
            throw error;
        }
    },
};
