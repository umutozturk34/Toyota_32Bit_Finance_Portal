export function transformCandles(raw) {
  if (!raw || raw.length === 0) return null;
  return {
    candles: raw.map(c => {
      const close = c.close ?? c.price;
      const hasOHLC = c.open != null && c.high != null && c.low != null;
      return {
        date: c.candleDate,
        candleDate: c.candleDate,
        open: hasOHLC ? c.open : close,
        high: hasOHLC ? c.high : close,
        low: hasOHLC ? c.low : close,
        close,
        volume: c.volume ?? null,
        investorCount: c.investorCount,
        portfolioSize: c.portfolioSize,
        shareCount: c.shareCount,
        bulletinPrice: c.bulletinPrice,
      };
    }),
  };
}

export const transformFundCandles = transformCandles;
