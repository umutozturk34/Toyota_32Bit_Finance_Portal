import api from '../../../shared/services/api';

export const adminService = {
  triggerCryptoSnapshot: () => api.post('/admin/trigger/crypto/snapshot').then(r => r.data.data),
  triggerCryptoCandles: () => api.post('/admin/trigger/crypto/candles').then(r => r.data.data),
  triggerCryptoFull: () => api.post('/admin/trigger/crypto/full').then(r => r.data.data),

  triggerStockSnapshot: () => api.post('/admin/trigger/stock/snapshot').then(r => r.data.data),
  triggerStockCandles: () => api.post('/admin/trigger/stock/candles').then(r => r.data.data),
  triggerStockFull: () => api.post('/admin/trigger/stock/full').then(r => r.data.data),

  triggerForexSnapshot: () => api.post('/admin/trigger/forex/snapshot').then(r => r.data.data),
  triggerForexCandles: () => api.post('/admin/trigger/forex/candles').then(r => r.data.data),
  triggerForexFull: () => api.post('/admin/trigger/forex/full').then(r => r.data.data),

  triggerFundSnapshot: () => api.post('/admin/trigger/fund/snapshot').then(r => r.data.data),
  triggerFundCandles: () => api.post('/admin/trigger/fund/candles').then(r => r.data.data),
  triggerFundFull: () => api.post('/admin/trigger/fund/full').then(r => r.data.data),

  triggerCommoditySnapshot: () => api.post('/admin/trigger/commodity/snapshot').then(r => r.data.data),
  triggerCommodityCandles: () => api.post('/admin/trigger/commodity/candles').then(r => r.data.data),
  triggerCommodityFull: () => api.post('/admin/trigger/commodity/full').then(r => r.data.data),

  triggerViopFull: () => api.post('/admin/trigger/viop/full').then(r => r.data.data),

  triggerBondUpdate: () => api.post('/admin/trigger/bond/update').then(r => r.data.data),
  triggerNewsUpdate: () => api.post('/admin/trigger/news/update').then(r => r.data.data),

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

  deleteTrackedAsset: async (type, code) => {
    const response = await api.delete(`/admin/tracked-assets/${type}/${encodeURIComponent(code)}`);
    return response.data;
  },
};

export { trackedAssetService } from '../../../shared/services/marketService';
