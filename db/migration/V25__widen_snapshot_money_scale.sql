-- Widen the daily-snapshot money columns from numeric(19,4) to numeric(23,8).
--
-- The snapshot math already runs at MoneyScale.PRICE = 8, but the columns stored only 4 decimals, so a
-- sub-0.0001 TRY holding (e.g. 0.000001 units of a ~46 TRY asset, market value 0.000046 TRY) was truncated
-- to 0.00000000 on write. The asset-detail chart/tooltip, the Y-axis and the daily P/L card read those
-- zeroed columns and showed ₺0,00 for a value the live aggregate cards correctly show as ₺0,00005.
--
-- Integer headroom is preserved: 23 - 8 = 15 integer digits, exactly the previous 19 - 4. Percent columns
-- (pnl_percent, daily_pnl_percent) are intentionally left at numeric(19,4). The ALTER only fixes rows
-- written after it; recompute/backfill the snapshots to recover precision for already-truncated history.

ALTER TABLE public.portfolio_asset_daily_snapshots
    ALTER COLUMN unit_price_try   TYPE numeric(23, 8),
    ALTER COLUMN market_value_try TYPE numeric(23, 8),
    ALTER COLUMN total_cost_try   TYPE numeric(23, 8),
    ALTER COLUMN pnl_try          TYPE numeric(23, 8),
    ALTER COLUMN daily_pnl_try    TYPE numeric(23, 8);

ALTER TABLE public.portfolio_daily_snapshots
    ALTER COLUMN total_value_try TYPE numeric(23, 8),
    ALTER COLUMN cash_try        TYPE numeric(23, 8),
    ALTER COLUMN total_cost_try  TYPE numeric(23, 8),
    ALTER COLUMN total_pnl_try   TYPE numeric(23, 8),
    ALTER COLUMN daily_pnl_try   TYPE numeric(23, 8);
