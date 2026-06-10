// Per-day analytics over an ALREADY display-currency-converted candle series (AssetDetailPage.convertCandleSet
// runs convertAt per FX date before the chart sees the data), so every metric below is currency-aware for free:
// percentages are currency-neutral and money values (close, range, ATR) are already in the display currency.
//
// SOLID note: this module is the single, reusable analysis engine. Every price metric reads through closeAt (the
// one universal "what is this candle worth" accessor), and each metric self-disables (returns null) when the
// candle set lacks the field it needs — so consumers never branch per asset type. describeCandles() exposes the
// capability set and analyze() is the one-call facade the live panel (and any future asset surface) renders from.

const TRADING_DAYS = 252;
// Mirror backend AssetReturnsService risk bands so the chart panel and the returns ranking agree.
const RISK_LOW_MAX = 25;
const RISK_MEDIUM_MAX = 55;

const num = (v) => (v != null && Number.isFinite(Number(v)) ? Number(v) : null);

export function closeAt(candles, i) {
  const c = candles?.[i];
  if (!c) return null;
  // close/price for spot & funds; sellingPrice is the forex "close" proxy; rate/price for bonds.
  const v = Number(c.close ?? c.price ?? c.sellingPrice ?? c.rate);
  return Number.isFinite(v) ? v : null;
}

/** Day-over-day change at index i (vs the previous candle's close). */
export function dailyChange(candles, i) {
  if (i == null || i <= 0) return null;
  const cur = closeAt(candles, i);
  const prev = closeAt(candles, i - 1);
  if (cur == null || prev == null || prev === 0) return null;
  return { value: cur - prev, percent: ((cur - prev) / prev) * 100 };
}

/** Cumulative return from the first candle to index i (defaults to the latest candle). */
export function periodReturn(candles, i) {
  if (!candles?.length) return null;
  const end = i == null ? candles.length - 1 : i;
  const start = closeAt(candles, 0);
  const cur = closeAt(candles, end);
  if (start == null || cur == null || start === 0) return null;
  return { value: cur - start, percent: ((cur - start) / start) * 100 };
}

/** Annualized volatility (%) over the whole series: sample stddev of daily log returns × √252 × 100. */
export function annualizedVolatility(candles) {
  if (!candles?.length) return null;
  const closes = [];
  for (let k = 0; k < candles.length; k++) {
    const c = closeAt(candles, k);
    if (c != null && c > 0) closes.push(c);
  }
  if (closes.length < 4) return null;
  const logReturns = [];
  for (let k = 1; k < closes.length; k++) logReturns.push(Math.log(closes[k] / closes[k - 1]));
  const mean = logReturns.reduce((s, r) => s + r, 0) / logReturns.length;
  const variance = logReturns.reduce((s, r) => s + (r - mean) ** 2, 0) / (logReturns.length - 1);
  return Math.sqrt(variance) * Math.sqrt(TRADING_DAYS) * 100;
}

/** Map an annualized volatility % to a risk band (same thresholds as the backend). */
export function riskBand(volatilityPct) {
  if (volatilityPct == null) return null;
  if (volatilityPct < RISK_LOW_MAX) return 'LOW';
  if (volatilityPct < RISK_MEDIUM_MAX) return 'MEDIUM';
  return 'HIGH';
}

/** Highest close, lowest close, and where index i sits between them (0..100). */
export function periodHighLow(candles, i) {
  if (!candles?.length) return null;
  let min = null;
  let max = null;
  for (let k = 0; k < candles.length; k++) {
    const v = closeAt(candles, k);
    if (v == null) continue;
    if (min == null || v < min) min = v;
    if (max == null || v > max) max = v;
  }
  if (min == null || max == null) return null;
  const cur = closeAt(candles, i == null ? candles.length - 1 : i);
  const positionPct = max > min && cur != null ? ((cur - min) / (max - min)) * 100 : null;
  return { high: max, low: min, positionPct };
}

/** Peak-to-trough spread of the period as a % of the low (derived from periodHighLow, so DRY). */
export function rangePct(candles) {
  const hl = periodHighLow(candles, null);
  if (!hl || hl.low == null || hl.low <= 0) return null;
  return { high: hl.high, low: hl.low, percent: ((hl.high - hl.low) / hl.low) * 100 };
}

/** Largest peak-to-trough decline over the series, as a non-positive % (running-peak method). */
export function maxDrawdown(candles) {
  if (!candles?.length) return null;
  let peak = null;
  let worst = 0;
  for (let k = 0; k < candles.length; k++) {
    const v = closeAt(candles, k);
    if (v == null || v <= 0) continue;
    if (peak == null || v > peak) peak = v;
    if (peak > 0) {
      const dd = ((v - peak) / peak) * 100;
      if (dd < worst) worst = dd;
    }
  }
  return { percent: worst };
}

/**
 * ATR-like average true range over the trailing window: mean (high−low) where real OHLC exists, otherwise mean
 * absolute day-over-day close move. Returned both in price units and as a % of the latest close.
 */
export function atrLike(candles, window = 14) {
  if (!candles?.length) return null;
  const n = candles.length;
  const start = Math.max(0, n - window);
  let sum = 0;
  let cnt = 0;
  for (let k = start; k < n; k++) {
    const c = candles[k];
    const high = num(c?.high);
    const low = num(c?.low);
    if (high != null && low != null && high !== low) {
      sum += high - low;
      cnt++;
    } else if (k > 0) {
      const cur = closeAt(candles, k);
      const prev = closeAt(candles, k - 1);
      if (cur != null && prev != null) {
        sum += Math.abs(cur - prev);
        cnt++;
      }
    }
  }
  if (!cnt) return null;
  const value = sum / cnt;
  const last = closeAt(candles, n - 1);
  return { value, percent: last && last > 0 ? (value / last) * 100 : null };
}

/** Signed run length of consecutive same-direction days ending at i (+up / −down). */
export function streak(candles, i) {
  const end = i == null ? (candles?.length ?? 0) - 1 : i;
  if (!candles?.length || end <= 0) return 0;
  let dir = 0;
  let count = 0;
  for (let k = end; k > 0; k--) {
    const cur = closeAt(candles, k);
    const prev = closeAt(candles, k - 1);
    if (cur == null || prev == null) break;
    // A flat day (weekend/holiday/no-change) doesn't reset the streak — skip it so an up/down run continues
    // across it rather than breaking on a zero-change day.
    if (cur === prev) continue;
    const d = cur > prev ? 1 : -1;
    if (dir === 0) {
      dir = d;
      count = 1;
    } else if (d === dir) {
      count++;
    } else {
      break;
    }
  }
  return dir * count;
}

/** Simple moving average of the close over the n days ending at i. */
export function sma(candles, n, i) {
  const end = i == null ? (candles?.length ?? 0) - 1 : i;
  if (!candles?.length || n <= 0 || end < n - 1) return null;
  let sum = 0;
  for (let k = end - n + 1; k <= end; k++) {
    const v = closeAt(candles, k);
    if (v == null) return null;
    sum += v;
  }
  return sum / n;
}

/** SMA(n) plus the close's distance from it as a % — the actionable "above/below the average" read. */
export function smaInfo(candles, n, i) {
  const m = sma(candles, n, i);
  if (m == null || m === 0) return null;
  const cur = closeAt(candles, i == null ? (candles?.length ?? 0) - 1 : i);
  return { value: m, distancePct: cur != null ? ((cur - m) / m) * 100 : null };
}

/** Mean of a raw numeric candle field (volume / investorCount / portfolioSize) over an optional trailing window. */
export function averageOf(candles, field, window) {
  if (!candles?.length || !field) return null;
  const n = candles.length;
  const start = window ? Math.max(0, n - window) : 0;
  let sum = 0;
  let cnt = 0;
  for (let k = start; k < n; k++) {
    const v = Number(candles[k]?.[field]);
    if (Number.isFinite(v)) {
      sum += v;
      cnt++;
    }
  }
  return cnt ? sum / cnt : null;
}

/** Forex bid/ask spread for a single candle, in price units and as a % of the buying price. */
export function forexSpread(candle) {
  if (!candle) return null;
  const s = num(candle.sellingPrice);
  const b = num(candle.buyingPrice);
  if (s == null || b == null) return null;
  return { value: s - b, percent: b > 0 ? ((s - b) / b) * 100 : null };
}

/**
 * Capability descriptor for a candle set: which fields/metrics are actually present, so consumers render
 * generically (Open/Closed — a new asset type needs no consumer changes, only the right fields on its candles).
 */
export function describeCandles(candles, assetType) {
  const arr = candles || [];
  const any = (pred) => arr.some(pred);
  return {
    assetType: assetType || null,
    isBond: assetType === 'BOND',
    hasOHL: any((c) => {
      const h = Number(c?.high);
      const l = Number(c?.low);
      return Number.isFinite(h) && Number.isFinite(l) && h !== l;
    }),
    hasVolume: any((c) => Number(c?.volume) > 0),
    hasInvestorCount: any((c) => Number(c?.investorCount) > 0),
    hasPortfolioSize: any((c) => Number(c?.portfolioSize) > 0),
    hasBulletin: any((c) => num(c?.bulletinPrice) != null),
    hasForexLegs: any((c) => num(c?.sellingPrice) != null && num(c?.buyingPrice) != null),
  };
}

/**
 * Series-level metrics — the O(n) work that depends ONLY on the candle set, not the hovered index. Compute this
 * ONCE per data change (memoize on candles) so hovering a long range doesn't re-run volatility/drawdown/range/
 * hi-lo/averages on every mouse move.
 */
export function analyzeSeries(candles, assetType) {
  if (!candles?.length) return null;
  const capabilities = describeCandles(candles, assetType);
  const vol = annualizedVolatility(candles);
  const hl = periodHighLow(candles, null);
  return {
    capabilities,
    volatility: vol,
    risk: riskBand(vol),
    drawdown: maxDrawdown(candles),
    range: rangePct(candles),
    atr: atrLike(candles),
    hiBound: hl?.high ?? null,
    loBound: hl?.low ?? null,
    avgVolume: capabilities.hasVolume ? averageOf(candles, 'volume') : null,
    avgInvestorCount: capabilities.hasInvestorCount ? averageOf(candles, 'investorCount') : null,
    avgPortfolioSize: capabilities.hasPortfolioSize ? averageOf(candles, 'portfolioSize') : null,
  };
}

/**
 * Index-level metrics for the candle at `index` (defaults to latest) + raw field values, merged onto a
 * precomputed `series` (from analyzeSeries). Only light per-index work runs here (close/daily/period/streak/sma/
 * fields), so it's cheap to call on every hover even over thousands of candles. Inapplicable metrics come back
 * null, so the live panel just maps the result — no per-asset-type branching in the view layer.
 */
export function analyzeAt(candles, index, series) {
  if (!candles?.length) return null;
  const n = candles.length;
  const i = index == null || index < 0 || index >= n ? n - 1 : index;
  const c = candles[i] || {};
  const high = num(c.high);
  const low = num(c.low);
  const close = closeAt(candles, i);
  const s = series || {};
  const positionPct = (s.hiBound != null && s.loBound != null && s.hiBound > s.loBound && close != null)
    ? ((close - s.loBound) / (s.hiBound - s.loBound)) * 100
    : null;
  return {
    index: i,
    date: c.candleDate || c.date || null,
    capabilities: s.capabilities ?? null,
    close,
    prevClose: i > 0 ? closeAt(candles, i - 1) : null,
    daily: dailyChange(candles, i),
    period: periodReturn(candles, i),
    volatility: s.volatility ?? null,
    risk: s.risk ?? null,
    drawdown: s.drawdown ?? null,
    range: s.range ?? null,
    atr: s.atr ?? null,
    streak: streak(candles, i),
    hiLo: { high: s.hiBound ?? null, low: s.loBound ?? null, positionPct },
    sma20: smaInfo(candles, 20, i),
    sma50: smaInfo(candles, 50, i),
    avgVolume: s.avgVolume ?? null,
    avgInvestorCount: s.avgInvestorCount ?? null,
    avgPortfolioSize: s.avgPortfolioSize ?? null,
    fields: {
      open: num(c.open),
      high,
      low,
      hasRealOHL: high != null && low != null && high !== low,
      sellingPrice: num(c.sellingPrice),
      buyingPrice: num(c.buyingPrice),
      effectiveBuyingPrice: num(c.effectiveBuyingPrice),
      effectiveSellingPrice: num(c.effectiveSellingPrice),
      spread: forexSpread(c),
      bulletinPrice: num(c.bulletinPrice),
      investorCount: num(c.investorCount),
      portfolioSize: num(c.portfolioSize),
      volume: num(c.volume),
    },
  };
}

/**
 * One-call facade (composes series + index). Prefer analyzeSeries + analyzeAt in hot paths (hover) to avoid
 * recomputing the O(n) series metrics on every index change.
 */
export function analyze(candles, index, assetType) {
  if (!candles?.length) return null;
  return analyzeAt(candles, index, analyzeSeries(candles, assetType));
}
