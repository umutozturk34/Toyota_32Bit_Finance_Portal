// Largest-remainder (Hamilton) percentages: each value's share rounded to `decimals` places such that
// the set sums to EXACTLY 100 — no rounding drift. Independently rounding each share (value/total*100
// then toFixed) makes a pie/legend read e.g. 46,9 + 29,0 + 14,3 + 9,2 + 0,7 = 100,1, which looks wrong;
// this distributes the leftover last-digit units to the largest fractional remainders so the labels add
// up to 100,0. Mirrors the backend PortfolioPdfService allocation rounding so screen and PDF agree.
// Negative/NaN inputs are clamped to 0 (allocation shares are non-negative magnitudes).
export function largestRemainderPercents(values, decimals = 1) {
  const nums = (values || []).map((v) => {
    const n = Number(v);
    return Number.isFinite(n) && n > 0 ? n : 0;
  });
  const total = nums.reduce((a, b) => a + b, 0);
  if (total <= 0) return nums.map(() => 0);
  const factor = 10 ** decimals;
  const targetUnits = 100 * factor;
  const raw = nums.map((v) => (v / total) * targetUnits);
  const units = raw.map((r) => Math.floor(r));
  let leftover = targetUnits - units.reduce((a, b) => a + b, 0);
  const byRemainder = raw
    .map((r, i) => ({ i, frac: r - Math.floor(r) }))
    .sort((a, b) => b.frac - a.frac);
  for (let k = 0; k < byRemainder.length && leftover > 0; k += 1) {
    units[byRemainder[k].i] += 1;
    leftover -= 1;
  }
  return units.map((u) => u / factor);
}

// Display-only share formatter: returns the percent number rounded to `decimals` (e.g. "12.3"), EXCEPT a
// slice that genuinely exists (|value| > 0) yet rounds to 0 at that precision, which returns the sentinel
// "<0.1" (for decimals=1) so a real holding never reads as exactly 0%. The caller appends the "%" glyph.
// The numeric shares from largestRemainderPercents are left untouched (still sum to 100); this only changes
// how a sub-resolution slice is labelled.
export function formatSharePct(pct, value, decimals = 1) {
  const p = Number(pct);
  const safePct = Number.isFinite(p) ? p : 0;
  const v = Number(value);
  if (Number.isFinite(v) && Math.abs(v) > 0 && Number(safePct.toFixed(decimals)) === 0) {
    return `<${(1 / 10 ** decimals).toFixed(decimals)}`;
  }
  return safePct.toFixed(decimals);
}
