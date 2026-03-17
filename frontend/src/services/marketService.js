import api from './api';
export const getCryptoById = async (id) => {
  try {
    const response = await api.get(`/market/${id}`);
    return response.data;
  } catch (error) {
    if (error.response?.status === 404) {
      console.log(`Crypto ${id} not found (404) - no data yet`);
      return null;
    }
    console.error(`Error fetching crypto ${id}:`, error);
    throw error;
  }
};
export const getCryptoHistory = async (id) => {
  try {
    const response = await api.get(`/market/${id}/history`);
    return response.data;
  } catch (error) {
    console.error(`Error fetching history for ${id}:`, error);
    throw error;
  }
};
export const getAllCryptos = async () => {
  try {
    const response = await api.get('/market');
    return response.data;
  } catch (error) {
    console.error('Error fetching all cryptos:', error);
    throw error;
  }
};
export const forexService = {
  getForexByCode: async (currencyCode) => {
    try {
      const response = await api.get(`/forex/${currencyCode}`);
      return response.data;
    } catch (error) {
      if (error.response?.status === 404) {
        console.log(`Forex ${currencyCode} not found (404) - no data yet`);
        return null;
      }
      console.error(`Error fetching forex ${currencyCode}:`, error);
      throw error;
    }
  },
  getAllForex: async () => {
    try {
      const response = await api.get('/forex');
      return response.data;
    } catch (error) {
      console.error('Error fetching all forex:', error);
      throw error;
    }
  },
  getForexHistory: async (currencyCode) => {
    try {
      const response = await api.get(`/forex/${currencyCode}/history`);
      return response.data;
    } catch (error) {
      console.error(`Error fetching history for ${currencyCode}:`, error);
      throw error;
    }
  },
};
export const stockService = {
  getStockBySymbol: async (symbol) => {
    try {
      const response = await api.get(`/stocks/${symbol}`);
      return response.data;
    } catch (error) {
      if (error.response?.status === 404) {
        console.log(`Stock ${symbol} not found (404) - no data yet`);
        return null;
      }
      console.error(`Error fetching stock ${symbol}:`, error);
      throw error;
    }
  },
  getAllStocks: async () => {
    try {
      const response = await api.get('/stocks');
      return response.data;
    } catch (error) {
      console.error('Error fetching all stocks:', error);
      throw error;
    }
  },
  getStockHistory: async (symbol) => {
    try {
      const response = await api.get(`/stocks/${symbol}/history`);
      return response.data;
    } catch (error) {
      console.error(`Error fetching history for ${symbol}:`, error);
      throw error;
    }
  },
};
export const adminService = {
  getTaskStatus: async () => {
    try {
      const response = await api.get('/admin/tasks/status');
      return response.data;
    } catch (error) {
      console.error('Error fetching task status:', error);
      throw error;
    }
  },
  triggerCryptoSnapshot: async () => {
    try {
      const response = await api.post('/admin/trigger/crypto/snapshot');
      return response.data;
    } catch (error) {
      console.error('Error triggering crypto snapshot:', error);
      throw error;
    }
  },
  triggerCryptoCandles: async () => {
    try {
      const response = await api.post('/admin/trigger/crypto/candles');
      return response.data;
    } catch (error) {
      console.error('Error triggering crypto candles:', error);
      throw error;
    }
  },
  triggerCryptoFull: async () => {
    try {
      const response = await api.post('/admin/trigger/crypto/full');
      return response.data;
    } catch (error) {
      console.error('Error triggering crypto full update:', error);
      throw error;
    }
  },
  triggerStockSnapshot: async () => {
    try {
      const response = await api.post('/admin/trigger/stock/snapshot');
      return response.data;
    } catch (error) {
      console.error('Error triggering stock snapshot:', error);
      throw error;
    }
  },
  triggerStockCandles: async () => {
    try {
      const response = await api.post('/admin/trigger/stock/candles');
      return response.data;
    } catch (error) {
      console.error('Error triggering stock candles:', error);
      throw error;
    }
  },
  triggerStockFull: async () => {
    try {
      const response = await api.post('/admin/trigger/stock/full');
      return response.data;
    } catch (error) {
      console.error('Error triggering stock full update:', error);
      throw error;
    }
  },
  triggerForexSnapshot: async () => {
    try {
      const response = await api.post('/admin/trigger/forex/snapshot');
      return response.data;
    } catch (error) {
      console.error('Error triggering forex snapshot update:', error);
      throw error;
    }
  },
  triggerForexCandles: async () => {
    try {
      const response = await api.post('/admin/trigger/forex/candles');
      return response.data;
    } catch (error) {
      console.error('Error triggering forex candles update:', error);
      throw error;
    }
  },
  triggerForexFull: async () => {
    try {
      const response = await api.post('/admin/trigger/forex/full');
      return response.data;
    } catch (error) {
      console.error('Error triggering forex full update:', error);
      throw error;
    }
  },
  triggerFundSnapshot: async () => {
    try {
      const response = await api.post('/admin/trigger/fund/snapshot');
      return response.data;
    } catch (error) {
      console.error('Error triggering fund snapshot:', error);
      throw error;
    }
  },
  triggerFundCandles: async () => {
    try {
      const response = await api.post('/admin/trigger/fund/candles');
      return response.data;
    } catch (error) {
      console.error('Error triggering fund candles:', error);
      throw error;
    }
  },
  triggerFundFull: async () => {
    try {
      const response = await api.post('/admin/trigger/fund/full');
      return response.data;
    } catch (error) {
      console.error('Error triggering fund full update:', error);
      throw error;
    }
  },
};
export const cryptoService = {
  getCryptos: async () => [],
  getCryptoDetails: async () => null,
};

export const fundService = {
  getAllFunds: async () => {
    try {
      const response = await api.get('/funds');
      return response.data;
    } catch (error) {
      console.error('Error fetching all funds:', error);
      throw error;
    }
  },
  getFundByCode: async (fundCode) => {
    try {
      const response = await api.get(`/funds/${fundCode}`);
      return response.data;
    } catch (error) {
      if (error.response?.status === 404) return null;
      console.error(`Error fetching fund ${fundCode}:`, error);
      throw error;
    }
  },
  getFundHistory: async (fundCode) => {
    try {
      const response = await api.get(`/funds/${fundCode}/history`);
      return response.data;
    } catch (error) {
      console.error(`Error fetching history for fund ${fundCode}:`, error);
      throw error;
    }
  },
};

export const getHistoricalData = getCryptoHistory;
