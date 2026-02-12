/**
 * Tracked BIST Stocks
 * Dynamically loaded from environment variable
 * Must match backend's BIST_STOCKS env variable
 */

// Parse BIST stocks from environment
const getBistStocks = () => {
  const bistStocksEnv = import.meta.env.VITE_BIST_STOCKS || '';
  
  if (!bistStocksEnv) {
    console.warn('⚠️ VITE_BIST_STOCKS environment variable not set');
    return [];
  }
  
  // Parse from environment (comma-separated)
  return bistStocksEnv
    .split(',')
    .map(s => s.trim())
    .filter(s => s.length > 0);
};

// Get stock symbols (returns array of symbols like ['THYAO.IS', 'GARAN.IS', ...])
export const getBistSymbols = () => {
  return getBistStocks();
};

// Get stock display names (without .IS suffix)
export const getBistDisplayName = (symbol) => {
  return symbol.replace('.IS', '');
};

// Check if a symbol is BIST stock
export const isBistStock = (symbol) => {
  return symbol && symbol.endsWith('.IS');
};

// Format stock symbol for API calls
export const formatBistSymbol = (displayName) => {
  if (displayName.endsWith('.IS')) {
    return displayName;
  }
  return `${displayName}.IS`;
};

export default {
  getBistSymbols,
  getBistDisplayName,
  isBistStock,
  formatBistSymbol
};
