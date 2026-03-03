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
export const getMultipleCryptos = async (ids) => {
  try {
    const promises = ids.map(id => getCryptoById(id));
    const results = await Promise.all(promises);
    return results.filter(crypto => crypto !== null);
  } catch (error) {
    console.error('Error fetching multiple cryptos:', error);
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
  getMultipleForex: async (currencyCodes) => {
    try {
      const promises = currencyCodes.map(code => forexService.getForexByCode(code));
      const results = await Promise.all(promises);
      return results.filter(forex => forex !== null);
    } catch (error) {
      console.error('Error fetching multiple forex:', error);
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
  getMultipleStocks: async (symbols) => {
    try {
      const promises = symbols.map(symbol => stockService.getStockBySymbol(symbol));
      const results = await Promise.all(promises);
      return results.filter(stock => stock !== null);
    } catch (error) {
      console.error('Error fetching multiple stocks:', error);
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
};
export const cryptoService = {
  getCryptos: async () => [],
  getCryptoDetails: async () => null,
};
export const getHistoricalData = getCryptoHistory;
