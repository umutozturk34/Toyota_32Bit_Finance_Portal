import api from './api';

export const newsService = {
    getAllNews: async (page = 0, size = 10) => {
        const response = await api.get(`/news?page=${page}&size=${size}`);
        return response.data;
    },
    
    getNewsByCategory: async (category, page = 0, size = 10) => {
        const response = await api.get(`/news/category/${category}?page=${page}&size=${size}`);
        return response.data;
    },
    
    getNewsById: async (id) => {
        const response = await api.get(`/news/${id}`);
        return response.data;
    },
    
    getRecentNews: async (days = 7, page = 0, size = 10) => {
        const response = await api.get(`/news/recent?days=${days}&page=${page}&size=${size}`);
        return response.data;
    }
};

export const exchangeRateService = {
    getLatestRates: async () => {
        const response = await api.get('/exchange-rates/latest');
        return response.data;
    },
    
    getRateByCurrency: async (currencyCode, date = null) => {
        let url = `/exchange-rates/${currencyCode}`;
        if (date) {
            url += `?date=${date}`;
        }
        const response = await api.get(url);
        return response.data;
    },
    
    getRateHistory: async (currencyCode) => {
        const response = await api.get(`/exchange-rates/${currencyCode}/history`);
        return response.data;
    }
};

export const metalService = {
    getLatestPrices: async () => {
        const response = await api.get('/metals/latest');
        return response.data;
    },
    
    getAllPrices: async (page = 0, size = 10) => {
        const response = await api.get(`/metals?page=${page}&size=${size}`);
        return response.data;
    },
    
    getPriceHistory: async (symbol) => {
        const response = await api.get(`/metals/${symbol}/history`);
        return response.data;
    }
};
