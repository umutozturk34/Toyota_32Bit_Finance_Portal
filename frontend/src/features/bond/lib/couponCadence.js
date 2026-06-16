// Day in milliseconds — the time grid the bond rate history is sampled on.
const DAY_MS = 86_400_000;

// Standard Turkish-Treasury coupon rhythms; an observed gap is snapped to the nearest one.
const STANDARD_CADENCES = [
  { key: 'monthly', days: 30 },
  { key: 'quarterly', days: 91 },
  { key: 'semiAnnual', days: 182 },
  { key: 'annual', days: 365 },
];

function nearestCadence(days) {
  return STANDARD_CADENCES.reduce((best, c) =>
    (Math.abs(c.days - days) < Math.abs(best.days - days) ? c : best));
}

/**
 * Infers a bond's coupon rhythm from the SHAPE of its clean-price history, not from its declared schedule. A
 * coupon payment shows up as an outlier day-over-day move in the indexed/clean price (most visible on CPI- and
 * gold-linked bonds, whose indexed price steps at each coupon); a plain fixed-coupon clean price barely moves, so
 * it produces no jumps and this returns null — exactly the "don't claim anything" case.
 *
 * Method: take the priced observations, measure the absolute % move between consecutive ones, flag the outliers
 * (≥ max(1%, 4× the median move)), collapse runs within ~10 days into one event, then — if at least two events are
 * regularly spaced (gaps within ±35% of their median) — snap the median gap to the nearest standard cadence. The
 * first jump's distance from the issue date is reported separately, so a single (non-repeating) jump still yields
 * "first jump N days after issue" without over-claiming a frequency.
 *
 * @param {Array<{date:string, price:number|null}>} history rate history, ascending or not
 * @param {string|Date|null} maturityStart the bond's issue date
 * @returns {{cadenceKey:string|null, approxDays:number|null, firstJumpDaysFromStart:number|null, jumpCount:number}|null}
 *   null when the series is too sparse or shows no coupon-like jump.
 */
export function inferCouponCadence(history, maturityStart) {
  if (!Array.isArray(history)) return null;
  const priced = history
    .filter((d) => d && d.price != null && d.date)
    .map((d) => ({ t: new Date(d.date).getTime(), p: Number(d.price) }))
    .filter((d) => Number.isFinite(d.t) && d.p > 0)
    .sort((a, b) => a.t - b.t);
  if (priced.length < 8) return null; // too few points to judge a rhythm

  // A coupon ex-date shows up as a sharp DOWNWARD crash — the dirty price sheds its accrued interest the day the
  // coupon is paid. So detect DROPS specifically (signed move ≤ a negative floor), NOT absolute moves: the gradual
  // ramp-up between coupons also has volatile up-days, and counting those would scramble the rhythm and surface a
  // spurious "first jump 1 day after issue". Noise floor = median absolute daily move; a drop must clear 4× that
  // and a 1.5% floor.
  const moves = [];
  for (let i = 1; i < priced.length; i += 1) {
    moves.push({ t: priced[i].t, chg: ((priced[i].p - priced[i - 1].p) / priced[i - 1].p) * 100 });
  }
  const absSorted = moves.map((m) => Math.abs(m.chg)).sort((a, b) => a - b);
  const median = absSorted[Math.floor(absSorted.length / 2)] || 0;
  const dropFloor = -Math.max(1.5, median * 4);

  // Drop days (negative outliers), with runs collapsed into one event (a crash can register over a couple of prints).
  const drops = [];
  for (const m of moves) {
    if (m.chg > dropFloor) continue;
    if (drops.length === 0 || (m.t - drops[drops.length - 1]) > 10 * DAY_MS) drops.push(m.t);
    else drops[drops.length - 1] = m.t;
  }
  if (drops.length === 0) return null;

  const startT = maturityStart ? new Date(maturityStart).getTime() : NaN;
  const firstJumpDaysFromStart = Number.isFinite(startT)
    ? Math.round((drops[0] - startT) / DAY_MS)
    : null;

  if (drops.length < 2) {
    return { cadenceKey: null, approxDays: null, firstJumpDaysFromStart, jumpCount: drops.length };
  }

  const gaps = [];
  for (let i = 1; i < drops.length; i += 1) gaps.push((drops[i] - drops[i - 1]) / DAY_MS);
  const gapMedian = gaps.slice().sort((a, b) => a - b)[Math.floor(gaps.length / 2)];
  const regular = gaps.every((g) => Math.abs(g - gapMedian) <= Math.max(20, gapMedian * 0.35));
  const cadence = regular ? nearestCadence(gapMedian) : null;

  return {
    cadenceKey: cadence ? cadence.key : null,
    approxDays: cadence ? Math.round(gapMedian) : null,
    firstJumpDaysFromStart,
    jumpCount: drops.length,
  };
}
