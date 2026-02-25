const getBistStocks = () => {
  const bistStocksEnv = import.meta.env.VITE_BIST_STOCKS || '';
  if (!bistStocksEnv) {
    console.warn(' VITE_BIST_STOCKS environment variable not set');
    return [];
  }
  return bistStocksEnv
    .split(',')
    .map(s => s.trim())
    .filter(s => s.length > 0);
};
export const getBistSymbols = () => {
  return getBistStocks();
};
export const getBistDisplayName = (symbol) => {
  return symbol.replace('.IS', '');
};
export const isBistStock = (symbol) => {
  return symbol && symbol.endsWith('.IS');
};
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
