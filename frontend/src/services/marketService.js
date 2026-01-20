import axios from './api';

export const stockService = {
    getAllStocks: (page = 0, size = 50) => {
        return axios.get(`/stocks?page=${page}&size=${size}`);
    },

    getUSStocks: (page = 0, size = 50) => {
        return axios.get(`/stocks/us?page=${page}&size=${size}`);
    },

    getBISTStocks: (page = 0, size = 50) => {
        return axios.get(`/stocks/bist?page=${page}&size=${size}`);
    },

    getBISTFunds: (page = 0, size = 50) => {
        return axios.get(`/stocks/bist-fund?page=${page}&size=${size}`);
    },

    getBISTIndex: () => {
        return axios.get(`/stocks/bist-index`);
    },

    getStockBySymbol: (symbol) => {
        return axios.get(`/stocks/${symbol}`);
    },

    getStockHistory: (symbol) => {
        return axios.get(`/stocks/${symbol}/history`);
    },

    fetchUSStocks: () => {
        return axios.post('/stocks/fetch/us');
    },

    fetchBISTStocks: () => {
        return axios.post('/stocks/fetch/bist');
    }
};

export const cryptoService = {
    getLatestCryptos: (page = 0, size = 20) => {
        return axios.get(`/crypto?page=${page}&size=${size}`);
    },

    getCryptoBySymbol: (symbol) => {
        return axios.get(`/crypto/${symbol}`);
    },

    getCryptoHistory: (symbol) => {
        return axios.get(`/crypto/${symbol}/history`);
    },

    fetchCryptos: () => {
        return axios.post('/crypto/fetch');
    }
};
