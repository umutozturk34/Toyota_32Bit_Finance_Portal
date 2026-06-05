export function computeViopAggregate(assetType, lots) {
  if (assetType !== 'VIOP') return null;
  const openLots = lots.filter((l) => l.exitDate == null);
  if (openLots.length === 0) return null;
  let totalQty = 0;
  let weightedNum = 0;
  let totalMarket = 0;
  let totalPnl = 0;
  let earliest = null;
  let currentPrice = null;
  for (const l of openLots) {
    const q = Number(l.quantity) || 0;
    const ep = Number(l.entryPrice) || 0;
    totalQty += q;
    weightedNum += q * ep;
    totalMarket += Number(l.marketValueTry) || 0;
    totalPnl += Number(l.pnlTry) || 0;
    if (l.entryDate && (!earliest || new Date(l.entryDate) < new Date(earliest))) {
      earliest = l.entryDate;
    }
    if (currentPrice == null && l.currentPriceTry != null) currentPrice = Number(l.currentPriceTry);
  }
  const weightedAvg = totalQty > 0 ? weightedNum / totalQty : 0;
  const pnlPercent = weightedAvg > 0 && totalQty > 0
    ? (totalPnl / (weightedAvg * totalQty)) * 100
    : 0;
  return {
    lotCount: openLots.length,
    totalQuantity: totalQty,
    weightedAvgEntryPrice: weightedAvg,
    earliestEntryDate: earliest,
    currentPriceTry: currentPrice,
    totalMarketValueTry: totalMarket,
    totalPnlTry: totalPnl,
    pnlPercent,
  };
}

export function computeClosedAggregate(lots) {
  if (lots.length === 0) return null;
  const allClosed = lots.every((l) => l.exitDate != null);
  if (!allClosed) return null;
  let totalEntryQty = 0;
  let weightedNum = 0;
  let totalRealizedPnl = 0;
  let totalClosedMarket = 0;
  let earliest = null;
  // For a fully-closed lot the backend freezes currentPriceTry to the exit/close price and marketValueTry
  // to the close-frozen notional (PortfolioSummaryService / DerivativePositionFormatter). Surfacing the first
  // lot's close price keeps the "Güncel Fiyat" card showing the real realized price instead of a phantom 0.
  let closePrice = null;
  for (const l of lots) {
    const q = Number(l.quantity) || 0;
    const ep = Number(l.entryPrice) || 0;
    totalEntryQty += q;
    weightedNum += q * ep;
    totalRealizedPnl += Number(l.realizedPnlTry ?? l.pnlTry ?? 0);
    totalClosedMarket += Number(l.marketValueTry) || 0;
    if (closePrice == null && l.currentPriceTry != null) closePrice = Number(l.currentPriceTry);
    if (l.entryDate && (!earliest || new Date(l.entryDate) < new Date(earliest))) {
      earliest = l.entryDate;
    }
  }
  const weightedAvg = totalEntryQty > 0 ? weightedNum / totalEntryQty : 0;
  const pnlPercent = weightedAvg > 0 && totalEntryQty > 0
    ? (totalRealizedPnl / (weightedAvg * totalEntryQty)) * 100
    : 0;
  return {
    lotCount: lots.length,
    totalQuantity: totalEntryQty,
    weightedAvgEntryPrice: weightedAvg,
    earliestEntryDate: earliest,
    currentPriceTry: closePrice,
    totalMarketValueTry: totalClosedMarket,
    totalPnlTry: totalRealizedPnl,
    pnlPercent,
  };
}

export function computeSeriesEndTs(lots) {
  if (lots.length === 0) return null;
  const anyOpen = lots.some((l) => l.exitDate == null);
  if (anyOpen) return null;
  const exits = lots
    .map((l) => {
      if (!l.exitDate) return null;
      const d = new Date(l.exitDate);
      d.setHours(23, 59, 59, 999);
      return d.getTime();
    })
    .filter((n) => n != null);
  return exits.length > 0 ? Math.max(...exits) : null;
}
