import api from '../../shared/services/api';

export const adminService = {
  getTaskStatus: async () => {
    const response = await api.get('/admin/tasks/status');
    return response.data;
  },

  triggerCryptoSnapshot: () => api.post('/admin/trigger/crypto/snapshot').then(r => r.data),
  triggerCryptoCandles: () => api.post('/admin/trigger/crypto/candles').then(r => r.data),
  triggerCryptoFull: () => api.post('/admin/trigger/crypto/full').then(r => r.data),

  triggerStockSnapshot: () => api.post('/admin/trigger/stock/snapshot').then(r => r.data),
  triggerStockCandles: () => api.post('/admin/trigger/stock/candles').then(r => r.data),
  triggerStockFull: () => api.post('/admin/trigger/stock/full').then(r => r.data),

  triggerForexSnapshot: () => api.post('/admin/trigger/forex/snapshot').then(r => r.data),
  triggerForexCandles: () => api.post('/admin/trigger/forex/candles').then(r => r.data),
  triggerForexFull: () => api.post('/admin/trigger/forex/full').then(r => r.data),

  triggerFundSnapshot: () => api.post('/admin/trigger/fund/snapshot').then(r => r.data),
  triggerFundCandles: () => api.post('/admin/trigger/fund/candles').then(r => r.data),
  triggerFundFull: () => api.post('/admin/trigger/fund/full').then(r => r.data),

  triggerBondUpdate: () => api.post('/admin/trigger/bond/update').then(r => r.data),
  triggerNewsUpdate: () => api.post('/admin/trigger/news/update').then(r => r.data),

  getNewsSources: async (includeDisabled = true) => {
    const response = await api.get('/admin/news-sources', { params: { includeDisabled } });
    return response.data.data;
  },

  createNewsSource: async (payload) => {
    const response = await api.post('/admin/news-sources', payload);
    return response.data.data;
  },

  updateNewsSource: async (id, payload) => {
    const response = await api.put(`/admin/news-sources/${id}`, payload);
    return response.data.data;
  },

  setNewsSourceEnabled: async (id, enabled) => {
    const response = await api.patch(`/admin/news-sources/${id}/enabled`, null, { params: { enabled } });
    return response.data;
  },

  deleteNewsSource: async (id) => {
    const response = await api.delete(`/admin/news-sources/${id}`);
    return response.data;
  },

  upsertTrackedAsset: async (payload) => {
    const response = await api.post('/admin/tracked-assets', payload);
    return response.data.data;
  },

  updateTrackedAssetOrder: async (payload) => {
    const response = await api.patch('/admin/tracked-assets/order', payload);
    return response.data;
  },

  setTrackedAssetEnabled: async (type, code, enabled) => {
    const response = await api.patch(`/admin/tracked-assets/${type}/${encodeURIComponent(code)}/enabled`, null, {
      params: { enabled },
    });
    return response.data;
  },

  deleteTrackedAsset: async (type, code) => {
    const response = await api.delete(`/admin/tracked-assets/${type}/${encodeURIComponent(code)}`);
    return response.data;
  },
};

export const trackedAssetService = {
  getByType: async (type, includeDisabled = false) => {
    const response = await api.get('/tracked-assets', {
      params: { type, includeDisabled },
    });
    return response.data.data;
  },

  getOne: async (type, code) => {
    try {
      const response = await api.get(`/tracked-assets/${type}/${encodeURIComponent(code)}`);
      return response.data.data;
    } catch (error) {
      if (error.response?.status === 404) return null;
      throw error;
    }
  },
};
