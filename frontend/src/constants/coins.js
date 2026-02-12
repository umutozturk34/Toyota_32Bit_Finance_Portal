/**
 * Tracked Cryptocurrencies
 * Dynamically loaded from environment variable
 * Must match backend's TRACKED_CRYPTOS env variable
 */

// Icon and symbol mapping for known cryptocurrencies
const CRYPTO_METADATA = {
  'bitcoin': { symbol: 'BTC', name: 'Bitcoin', icon: '₿' },
  'ethereum': { symbol: 'ETH', name: 'Ethereum', icon: 'Ξ' },
  'tether': { symbol: 'USDT', name: 'Tether', icon: '₮' },
  'binancecoin': { symbol: 'BNB', name: 'BNB', icon: '🔶' },
  'solana': { symbol: 'SOL', name: 'Solana', icon: '◎' },
  'ripple': { symbol: 'XRP', name: 'XRP', icon: '✕' },
  'usd-coin': { symbol: 'USDC', name: 'USDC', icon: 'Ⓤ' },
  'cardano': { symbol: 'ADA', name: 'Cardano', icon: '₳' },
  'avalanche-2': { symbol: 'AVAX', name: 'Avalanche', icon: '🔺' },
  'dogecoin': { symbol: 'DOGE', name: 'Dogecoin', icon: 'Ð' },
  'tron': { symbol: 'TRX', name: 'TRON', icon: '◈' },
  'polkadot': { symbol: 'DOT', name: 'Polkadot', icon: '●' }
};

// Parse tracked cryptocurrencies from environment
const getTrackedCryptos = () => {
  const cryptosEnv = import.meta.env.VITE_TRACKED_CRYPTOS || '';
  
  if (!cryptosEnv) {
    console.warn('⚠️ VITE_TRACKED_CRYPTOS environment variable not set');
    return [];
  }
  
  // Parse from environment (comma-separated CoinGecko IDs)
  return cryptosEnv
    .split(',')
    .map(s => s.trim())
    .filter(s => s.length > 0);
};

// Build TRACKED_COINS array from environment
const buildTrackedCoins = () => {
  const cryptoIds = getTrackedCryptos();
  
  return cryptoIds.map(id => {
    const metadata = CRYPTO_METADATA[id] || {
      symbol: id.toUpperCase().slice(0, 4),
      name: id.charAt(0).toUpperCase() + id.slice(1),
      icon: '₿'
    };
    
    return {
      id,
      symbol: metadata.symbol,
      name: metadata.name,
      icon: metadata.icon
    };
  });
};

export const TRACKED_COINS = buildTrackedCoins();

// Helper functions
export const getCoinIds = () => TRACKED_COINS.map(coin => coin.id);

export const getCoinIcon = (symbol) => {
  const coin = TRACKED_COINS.find(c => c.symbol.toUpperCase() === symbol.toUpperCase());
  return coin?.icon || '₿';
};

export const getCoinBySymbol = (symbol) => {
  return TRACKED_COINS.find(c => c.symbol.toUpperCase() === symbol.toUpperCase());
};

export const getCoinIdBySymbol = (symbol) => {
  const coin = getCoinBySymbol(symbol);
  return coin ? coin.id : symbol.toLowerCase();
};

export const getCoinById = (id) => {
  return TRACKED_COINS.find(c => c.id === id);
};

export default {
  TRACKED_COINS,
  getCoinIds,
  getCoinIcon,
  getCoinBySymbol,
  getCoinIdBySymbol,
  getCoinById
};
