DELETE FROM portfolio_asset_daily_snapshots WHERE asset_type = 'CRYPTO';

DELETE FROM portfolio_daily_snapshots
WHERE portfolio_id IN (
    SELECT DISTINCT portfolio_id
    FROM portfolio_positions
    WHERE asset_type = 'CRYPTO'
);
