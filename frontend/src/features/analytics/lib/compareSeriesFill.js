// Series fill/backfill + compound helpers for Compare. Split out of compareSeriesUtils to keep that
// module under its line budget; these are the pure, date-driven series transforms (forward/back fill,
// daily expansion, gap carry, rate derivation, rate compounding). The two date primitives below are
// shared with the classification/currency side of compareSeriesUtils, which re-imports them from here.

// Parse a YYYY-MM-DD key as a LOCAL-midnight date so a cursor advanced via local setDate and the key
// emitted via local toLocaleDateString stay in the same zone. Building the cursor from `new Date(iso)`
// (UTC midnight) and reading it back via toISOString in a DST-observing zone drops or duplicates the
// spring-forward day. Istanbul is DST-free, but non-Turkey browsers corrupt the fill.
export const parseLocal = (iso) => {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
};

// Fast local YYYY-MM-DD (same shape as toLocaleDateString('sv-SE')) WITHOUT Intl. The day-by-day fill and
// compound loops below run tens of thousands of times on the ALL range × several mixed series, and Intl
// formatting was a measurable chunk of that recompute; manual zero-padding is identical output, far cheaper.
export const fmtLocal = (d) => {
  const y = d.getFullYear();
  const m = d.getMonth() + 1;
  const day = d.getDate();
  return `${y}-${m < 10 ? '0' : ''}${m}-${day < 10 ? '0' : ''}${day}`;
};

// Compound an annual-rate-% series into a cumulative growth index (starts at 1.0). Mirrors backend
// ScenarioService.applyCompound: daily compounding over a 365-day year, with the rate in effect
// during each interval applied over that interval's day count. When `endIso` is given, the last
// published rate is carried forward past the final observation (see the tail block below).
export function compoundRateSeries(points, endIso) {
  const sorted = [...(points || [])]
    .filter((p) => p && p.date && Number.isFinite(Number(p.value)))
    .sort((a, b) => String(a.date).localeCompare(String(b.date)));
  if (sorted.length === 0) return [];
  const out = [{ ...sorted[0], value: 1 }];
  let factor = 1;
  // Emit the compounding DAY-BY-DAY across each inter-observation gap, not one cumulative lump at the next
  // observation. Deposit/rate quotes are published ~monthly, so crediting a whole month's interest onto the
  // observation date — then letting forwardFillDaily carry the value flat in between — rendered the index as
  // a staircase (flat for a month, then a jump up) even though interest accrues continuously. Walking it
  // daily makes the line rise smoothly within a fixed-rate month, matching the tail-carry below. The end
  // value is mathematically identical: (1 + dailyRate)^days == applying (1 + dailyRate) once per day.
  for (let i = 1; i < sorted.length; i += 1) {
    const annualRatePct = Number(sorted[i - 1].value);
    const dailyRate = Number.isFinite(annualRatePct) ? annualRatePct / 100 / 365 : 0;
    const cursor = parseLocal(sorted[i - 1].date);
    const stepEnd = parseLocal(sorted[i].date);
    const days = Math.round((stepEnd - cursor) / 86400000);
    if (days <= 0) {
      out.push({ ...sorted[i], value: factor });
      continue;
    }
    for (let d = 1; d < days; d += 1) {
      cursor.setDate(cursor.getDate() + 1);
      factor *= (1 + dailyRate);
      out.push({ date: fmtLocal(cursor), value: factor, _filled: true });
    }
    // The final day of the gap IS the next observation date: compound it and attach the real point's
    // fields (raw rate, etc.) so the observation stays a non-synthetic anchor.
    factor *= (1 + dailyRate);
    out.push({ ...sorted[i], value: factor });
  }
  // Tail carry-forward: deposit/rate interest keeps accruing daily at the last published rate until a
  // newer one is published, so the index must continue to the window edge instead of flat-lining at the
  // final observation. Without this the post-publication days (e.g. ~13 for a weekly-published deposit)
  // silently drop their interest and Compare understates the deposit vs the backend ScenarioService
  // (which already carries the last rate to endDate in simulateRate). Emitted day-by-day so the tail
  // renders as a smooth continuation rather than a flat segment with a jump at the edge.
  const lastRate = Number(sorted[sorted.length - 1].value);
  if (endIso && Number.isFinite(lastRate)) {
    const dailyRate = lastRate / 100 / 365;
    let cursor = parseLocal(sorted[sorted.length - 1].date);
    const end = parseLocal(endIso);
    cursor.setDate(cursor.getDate() + 1);
    while (cursor <= end) {
      factor *= (1 + dailyRate);
      out.push({ date: fmtLocal(cursor), value: factor, _filled: true });
      cursor = new Date(cursor);
      cursor.setDate(cursor.getDate() + 1);
    }
  }
  return out;
}

export function forwardFillTo(points, endIso) {
  if (!points || points.length === 0) return points;
  const sorted = [...points].sort((a, b) => String(a.date).localeCompare(String(b.date)));
  const last = sorted[sorted.length - 1];
  if (last.date >= endIso) return sorted;
  return [...sorted, { ...last, date: endIso, value: last.value, _filled: true }];
}

export function forwardFillToToday(points) {
  return forwardFillTo(points, new Date().toISOString().slice(0, 10));
}

export function backFillToWindowStart(points, windowStart) {
  if (!points || points.length === 0) return points;
  const sorted = [...points].sort((a, b) => String(a.date).localeCompare(String(b.date)));
  const beforeOrAt = sorted.filter((p) => p.date <= windowStart);
  const inWindow = sorted.filter((p) => p.date > windowStart);
  if (beforeOrAt.length === 0) return inWindow;
  const anchor = beforeOrAt[beforeOrAt.length - 1];
  if (anchor.date === windowStart) return [anchor, ...inWindow];
  return [{ date: windowStart, value: anchor.value, _backfilled: true }, ...inWindow];
}

// Derive a YoY ('yoy') or MoM ('mom') RATE series (%) from a cumulative INDEX series:
// rate(t) = value(t) / value(t − span) − 1. Inflation/PPI/index-rates are stored as an ever-rising
// cumulative index, so this is what users read as the actual annual/monthly rate. The base is matched by
// nearest-earlier date within a tolerance (handles month-length drift); points without a usable base are
// skipped, so the caller must include ~12 months of history before the window start for full coverage.
export function deriveRateSeries(points, view) {
  if (!points || points.length === 0) return [];
  const sorted = [...points].sort((a, b) => String(a.date).localeCompare(String(b.date)));
  const months = view === 'mom' ? 1 : 12;
  const tolDays = view === 'mom' ? 16 : 25;
  const out = [];
  for (let i = 0; i < sorted.length; i += 1) {
    const cur = Number(sorted[i].value);
    if (!Number.isFinite(cur)) continue;
    const target = new Date(sorted[i].date);
    target.setMonth(target.getMonth() - months);
    let base = null;
    let bestDiff = Infinity;
    for (let j = i - 1; j >= 0; j -= 1) {
      const diff = Math.abs((new Date(sorted[j].date).getTime() - target.getTime()) / 86_400_000);
      if (diff < bestDiff) { bestDiff = diff; base = sorted[j]; }
    }
    if (!base || bestDiff > tolDays) continue;
    const baseV = Number(base.value);
    if (!Number.isFinite(baseV) || baseV <= 0) continue;
    out.push({ date: sorted[i].date, value: (cur / baseV - 1) * 100 });
  }
  return out;
}

export function forwardFillDaily(points, fromIso, toIso) {
  if (!points || points.length === 0) return points;
  const sorted = [...points].sort((a, b) => String(a.date).localeCompare(String(b.date)));
  if (sorted.length > 1) {
    let maxGap = 0;
    for (let i = 1; i < sorted.length; i += 1) {
      const prev = parseLocal(sorted[i - 1].date);
      const curr = parseLocal(sorted[i].date);
      const gap = Math.round((curr - prev) / 86400000);
      if (gap > maxGap) maxGap = gap;
    }
    if (maxGap < 4) return sorted;
  }
  const result = [];
  let cursor = parseLocal(fromIso);
  const end = parseLocal(toIso);
  let idx = 0;
  let currentPoint = null;
  while (cursor <= end) {
    const cursorIso = fmtLocal(cursor);
    while (idx < sorted.length && sorted[idx].date <= cursorIso) {
      currentPoint = sorted[idx];
      idx += 1;
    }
    // currentPoint is the latest real point with date <= cursorIso (the idx-walk above advances it),
    // so when its date equals the cursor it IS that day's real point — no need to rescan `sorted` for
    // an exact match. The old per-day .find() made this O(days × points): on the ALL range a 1995→now
    // series ran ~11k days × ~11k points ≈ 120M scans PER series, the multi-second freeze.
    if (currentPoint !== null && currentPoint.date === cursorIso) {
      result.push(currentPoint);
    } else if (currentPoint !== null) {
      // Spread the carried point so extra fields (e.g. the portfolio's pnlTry) survive the fill.
      result.push({ ...currentPoint, date: cursorIso, value: currentPoint.value, _filled: true });
    }
    cursor = new Date(cursor);
    cursor.setDate(cursor.getDate() + 1);
  }
  return result;
}

// Carry the previous reading forward to every intervening day WITHOUT inflating the whole series to
// daily cadence the way forwardFillDaily does. For sparse macro lines (CPI, rates) this gives every
// in-between day the last published value, so the line and its tooltips are continuous across gaps.
export function forwardFillGaps(points) {
  if (!points || points.length < 2) return points;
  const sorted = [...points].sort((a, b) => String(a.date).localeCompare(String(b.date)));
  const result = [sorted[0]];
  for (let i = 1; i < sorted.length; i += 1) {
    const prev = sorted[i - 1];
    const curr = sorted[i];
    let cursor = parseLocal(prev.date);
    cursor.setDate(cursor.getDate() + 1);
    const currDate = parseLocal(curr.date);
    while (cursor < currDate) {
      result.push({ date: fmtLocal(cursor), value: prev.value, _filled: true });
      cursor = new Date(cursor);
      cursor.setDate(cursor.getDate() + 1);
    }
    result.push(curr);
  }
  return result;
}
