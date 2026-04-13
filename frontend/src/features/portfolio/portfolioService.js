import api from '../../shared/services/api';

const BASE = '/portfolios';

export const portfolioService = {
  initialize: async () => {
    const res = await api.post(`${BASE}/onboarding/initialize`);
    return res.data;
  },

  list: async () => {
    const res = await api.get(BASE);
    return res.data.data;
  },

  create: async (name) => {
    const res = await api.post(BASE, { name });
    return res.data.data;
  },

  getSummary: async (portfolioId, assetType = null) => {
    const params = {};
    if (assetType) params.assetType = assetType;
    const res = await api.get(`${BASE}/${portfolioId}/summary`, { params });
    return res.data.data;
  },

  getPositions: async (portfolioId, params = {}) => {
    const res = await api.get(`${BASE}/${portfolioId}/positions`, { params });
    return res.data.data;
  },

  getTransactions: async (portfolioId, params = {}) => {
    const res = await api.get(`${BASE}/${portfolioId}/transactions`, { params });
    return res.data.data;
  },

  executeTransaction: async (portfolioId, data) => {
    const res = await api.post(`${BASE}/${portfolioId}/transactions`, data);
    return res.data.data;
  },

  getAllocation: async (portfolioId, mode = 'assetType', assetType) => {
    const params = { mode };
    if (assetType) params.assetType = assetType;
    const res = await api.get(`${BASE}/${portfolioId}/allocation`, { params });
    return res.data.data;
  },

  getPerformance: async (portfolioId, range = '1M', assetType = null) => {
    const params = { type: 'performance', range };
    if (assetType) params.assetType = assetType;
    const res = await api.get(`${BASE}/${portfolioId}/chart`, { params });
    return res.data.data;
  },

  getAssetSeries: async (portfolioId, assetType, assetCode, range = '1M') => {
    const res = await api.get(`${BASE}/${portfolioId}/chart`, {
      params: { type: 'asset-series', assetType, assetCode, range },
    });
    return res.data.data;
  },

  getView: async (portfolioId, include = 'summary,positions,transactions,allocation') => {
    const res = await api.get(`${BASE}/${portfolioId}/view`, { params: { include } });
    return res.data.data;
  },

};
