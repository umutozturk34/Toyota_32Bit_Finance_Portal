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

const INDEX_LONG_NAMES = {
  'XU030.IS': 'BIST 30 Endeksi',
  'XU100.IS': 'BIST 100 Endeksi',
  'XU500.IS': 'BIST Tüm Endeksi',
  'XBANK.IS': 'BIST Banka Endeksi',
  'XUSIN.IS': 'BIST Sınai Endeksi',
  'XUTEK.IS': 'BIST Teknoloji Endeksi',
  'XGMYO.IS': 'BIST GYO Endeksi',
  'XHOLD.IS': 'BIST Holding Endeksi',
  'XULAS.IS': 'BIST Ulusal Endeksi',
  'XKTUM.IS': 'BIST Katılım Tüm Endeksi',
  'XKTMT.IS': 'BIST Katılım Temettü Endeksi',
  'XTRZM.IS': 'BIST Turizm Endeksi',
  'XGIDA.IS': 'BIST Gıda Endeksi',
  'XMANA.IS': 'BIST Metal Ana Sanayi Endeksi'
};

const MAIN_INDICES = ['XU030.IS', 'XU100.IS', 'XU500.IS'];
const SECONDARY_INDICES = ['XBANK.IS', 'XUSIN.IS', 'XUTEK.IS', 'XGMYO.IS', 'XHOLD.IS' ,'XULAS.IS', 'XKTUM.IS', 'XKTMT.IS', 'XTRZM.IS', 'XGIDA.IS', 'XMANA.IS'];
const ALL_INDICES = [...MAIN_INDICES, ...SECONDARY_INDICES];
const COMPARE_ONLY_SYMBOLS = [...SECONDARY_INDICES, 'XU500.IS'];

export const getBistSymbols = () => {
  return getBistStocks();
};
export const getBistDisplayName = (symbol) => {
  return symbol.replace('.IS', '');
};
export const getIndexLongName = (symbol) => {
  return INDEX_LONG_NAMES[symbol] || symbol;
};
export const isMainIndex = (symbol) => MAIN_INDICES.includes(symbol);
export const isSecondaryIndex = (symbol) => SECONDARY_INDICES.includes(symbol);
export const isIndex = (symbol) => ALL_INDICES.includes(symbol);
export const isCompareOnly = (symbol) => COMPARE_ONLY_SYMBOLS.includes(symbol);
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
  getIndexLongName,
  isMainIndex,
  isSecondaryIndex,
  isIndex,
  isCompareOnly,
  isBistStock,
  formatBistSymbol
};
