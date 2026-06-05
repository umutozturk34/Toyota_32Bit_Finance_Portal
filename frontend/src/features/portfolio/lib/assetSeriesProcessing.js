import { computeSeriesEndTs } from './assetAggregates';

// Pure processing of a single-asset chart series — no React state, no FX. Extracted from AssetDetail so the
// DirectionPanel stays a thin view over this.

/**
 * Truncates a raw asset series to the position's lifetime and drops a redundant zero tail.
 *
 * computeSeriesEndTs(lots) is non-null only when every lot is closed; we then truncate to the exit day so the
 * line ends at the close, not at today's stale snapshot. A post-close snapshot recorded ON the exit day (the
 * position now holding nothing) survives that day-level cut as a redundant 0 tail that drags the line to the
 * floor; drop trailing 0-value points as long as real (non-zero) data precedes them, so a genuinely flat-zero
 * series is left untouched.
 */
export function processAssetSeries(rawSeries, lots) {
  const seriesEndTs = computeSeriesEndTs(lots);
  if (seriesEndTs == null) return rawSeries;
  const truncated = rawSeries.filter((p) => new Date(p.timestamp).getTime() <= seriesEndTs);
  let end = truncated.length;
  while (end > 1 && !(Number(truncated[end - 1].marketValueTry) > 0)) end -= 1;
  return end < truncated.length && truncated.slice(0, end).some((p) => Number(p.marketValueTry) > 0)
    ? truncated.slice(0, end)
    : truncated;
}
