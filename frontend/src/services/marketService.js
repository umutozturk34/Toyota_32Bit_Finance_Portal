import api from './api';

/**
 * Crypto market data services
 */
export const getCryptoById = async (id) => {
  try {
    const response = await api.get(`/${id}`);
    return response.data;
  } catch (error) {
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
    return await Promise.all(promises);
  } catch (error) {
    console.error('Error fetching multiple cryptos:', error);
    throw error;
  }
};

// Legacy exports for backward compatibility
export const stockService = {
  getStocks: async () => [],
  getStockDetails: async () => null,
};

export const cryptoService = {
  getCryptos: async () => [],
  getCryptoDetails: async () => null,
};

export const getHistoricalData = getCryptoHistory;
