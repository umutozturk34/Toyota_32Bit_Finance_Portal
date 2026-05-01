ALTER TABLE portfolios
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE portfolio_positions
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE portfolio_daily_snapshots
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE portfolio_asset_daily_snapshots
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE portfolio_daily_snapshots
    ADD CONSTRAINT uq_portfolio_daily_snapshot UNIQUE (portfolio_id, snapshot_date);

ALTER TABLE portfolio_asset_daily_snapshots
    ADD CONSTRAINT uq_portfolio_asset_daily_snapshot
        UNIQUE (portfolio_id, asset_type, asset_code, snapshot_date);
