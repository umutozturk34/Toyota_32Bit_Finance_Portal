/**
 * Tracked Cryptocurrencies
 * Must match backend's MarketDataService.TRACKED_COINS
 */
export const TRACKED_COINS = [
  {
    id: 'bitcoin',
    symbol: 'BTC',
    name: 'Bitcoin',
    icon: '₿'
  },
  {
    id: 'ethereum',
    symbol: 'ETH',
    name: 'Ethereum',
    icon: 'Ξ'
  },
  {
    id: 'tether',
    symbol: 'USDT',
    name: 'Tether',
    icon: '₮'
  },
  {
    id: 'binancecoin',
    symbol: 'BNB',
    name: 'BNB',
    icon: '🔶'
  },
  {
    id: 'solana',
    symbol: 'SOL',
    name: 'Solana',
    icon: '◎'
  },
  {
    id: 'ripple',
    symbol: 'XRP',
    name: 'XRP',
    icon: '✕'
  },
  {
    id: 'usd-coin',
    symbol: 'USDC',
    name: 'USDC',
    icon: 'Ⓤ'
  },
  {
    id: 'cardano',
    symbol: 'ADA',
    name: 'Cardano',
    icon: '₳'
  },
  {
    id: 'avalanche-2',
    symbol: 'AVAX',
    name: 'Avalanche',
    icon: '🔺'
  },
  {
    id: 'dogecoin',
    symbol: 'DOGE',
    name: 'Dogecoin',
    icon: 'Ð'
  },
  {
    id: 'tron',
    symbol: 'TRX',
    name: 'TRON',
    icon: '◈'
  },
  {
    id: 'polkadot',
    symbol: 'DOT',
    name: 'Polkadot',
    icon: '●'
  }
];

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
