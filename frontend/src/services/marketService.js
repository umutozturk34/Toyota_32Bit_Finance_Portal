import api from './api';

/**
 * Crypto market data services
 */
export const getCryptoById = async (id) => {
  try {
    const response = await api.get(`/${id}`);
    return response.data;
  } catch (error) {
    // 404 means no data yet - return null instead of throwing
    if (error.response?.status === 404) {
      console.log(`Crypto ${id} not found (404) - no data yet`);
      return null;
    }
    console.error(`Error fetching crypto ${id}:`, error);
    throw error;
  }
};

/**
 * Crypto historical data for charts
 */
export const getCryptoHistory = async (id) => {
  try {
    const response = await api.get(`/${id}/history`);
    return response.data;
  } catch (error) {
    console.error(`Error fetching history for ${id}:`, error);
    throw error;
  }
};

/**
 * Fetch multiple cryptos at once
 * @param {string[]} ids - Array of crypto IDs
 * @returns {Promise} Array of crypto snapshots
 */
export const getMultipleCryptos = async (ids) => {
  try {
    const promises = ids.map(id => getCryptoById(id));
    const results = await Promise.all(promises);
    // Filter out null values (404s)
    return results.filter(crypto => crypto !== null);
  } catch (error) {
    console.error('Error fetching multiple cryptos:', error);
    throw error;
  }
};

/**
 * Stock market data services
 */
export const stockService = {
  // Get single stock by symbol (uses cache)
  getStockBySymbol: async (symbol) => {
    try {
      const response = await api.get(`/stocks/${symbol}`, { baseURL: '/api/v1' });
      return response.data;
    } catch (error) {
      // 404 means no data yet - return null instead of throwing
      if (error.response?.status === 404) {
        console.log(`Stock ${symbol} not found (404) - no data yet`);
        return null;
      }
      console.error(`Error fetching stock ${symbol}:`, error);
      throw error;
    }
  },
  
  // Fetch multiple stocks at once (uses cache for each)
  getMultipleStocks: async (symbols) => {
    try {
      const promises = symbols.map(symbol => stockService.getStockBySymbol(symbol));
      const results = await Promise.all(promises);
      // Filter out null values (404s)
      return results.filter(stock => stock !== null);
    } catch (error) {
      console.error('Error fetching multiple stocks:', error);
      throw error;
    }
  },
  
  // Get stock history
  getStockHistory: async (symbol) => {
    try {
      const response = await api.get(`/stocks/${symbol}/history`, { baseURL: '/api/v1' });
      return response.data;
    } catch (error) {
      console.error(`Error fetching history for ${symbol}:`, error);
      throw error;
    }
  },
};

/**
 * Admin trigger services
 */
export const adminService = {
  // Crypto triggers
  triggerCryptoSnapshot: async () => {
    try {
      const response = await api.post('/admin/trigger/crypto/snapshot', {}, { baseURL: '/api/v1' });
      return response.data;
    } catch (error) {
      console.error('Error triggering crypto snapshot:', error);
      throw error;
    }
  },
  
  triggerCryptoCandles: async () => {
    try {
      const response = await api.post('/admin/trigger/crypto/candles', {}, { baseURL: '/api/v1' });
      return response.data;
    } catch (error) {
      console.error('Error triggering crypto candles:', error);
      throw error;
    }
  },
  
  triggerCryptoFull: async () => {
    try {
      const response = await api.post('/admin/trigger/crypto/full', {}, { baseURL: '/api/v1' });
      return response.data;
    } catch (error) {
      console.error('Error triggering crypto full update:', error);
      throw error;
    }
  },
  
  // Stock triggers
  triggerStockSnapshot: async () => {
    try {
      const response = await api.post('/admin/trigger/stock/snapshot', {}, { baseURL: '/api/v1' });
      return response.data;
    } catch (error) {
      console.error('Error triggering stock snapshot:', error);
      throw error;
    }
  },
  
  triggerStockCandles: async () => {
    try {
      const response = await api.post('/admin/trigger/stock/candles', {}, { baseURL: '/api/v1' });
      return response.data;
    } catch (error) {
      console.error('Error triggering stock candles:', error);
      throw error;
    }
  },
  
  triggerStockFull: async () => {
    try {
      const response = await api.post('/admin/trigger/stock/full', {}, { baseURL: '/api/v1' });
      return response.data;
    } catch (error) {
      console.error('Error triggering stock full update:', error);
      throw error;
    }
  },
};

export const cryptoService = {
  getCryptos: async () => [],
  getCryptoDetails: async () => null,
};

export const getHistoricalData = getCryptoHistory;
