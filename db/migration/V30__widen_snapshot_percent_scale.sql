-- Widen the daily-snapshot percentage columns from numeric(19,4) to numeric(23,8).
--
-- V25 widened the money columns to (23,8) but deliberately left the percent columns at (19,4). That left
-- a mismatch: the percentages are computed at MoneyScale.PRICE (scale 8) in AssetRowSnapshotBuilder and
-- stored truncated to 4 decimals. DailyAggregationService.priorValueOf() then divides the daily PnL by the
-- truncated percent to recover the prior-day baseline, so the lost decimals propagate into the recovered
-- baseline and the daily K/Z percentage (a ~0.04% drift). Matching the column scale to the computation
-- scale removes the truncation; existing rows realign on the next snapshot recompute.
ALTER TABLE public.portfolio_asset_daily_snapshots
    ALTER COLUMN daily_pnl_percent TYPE numeric(23, 8);

ALTER TABLE public.portfolio_daily_snapshots
    ALTER COLUMN pnl_percent       TYPE numeric(23, 8),
    ALTER COLUMN daily_pnl_percent TYPE numeric(23, 8);
