
CREATE SEQUENCE IF NOT EXISTS public.portfolio_daily_snapshot_seq
    START WITH 1 INCREMENT BY 50 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE SEQUENCE IF NOT EXISTS public.portfolio_daily_snapshots_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.portfolio_daily_snapshots (
    id                 bigint NOT NULL DEFAULT nextval('public.portfolio_daily_snapshot_seq'::regclass),
    portfolio_id       bigint NOT NULL,
    snapshot_date      date NOT NULL,
    total_value_try    numeric(19,4) NOT NULL,
    total_cost_try     numeric(19,4) NOT NULL,
    total_pnl_try      numeric(19,4) NOT NULL,
    pnl_percent        numeric(19,4) NOT NULL,
    created_at         timestamp without time zone DEFAULT now() NOT NULL,
    version            bigint DEFAULT 0 NOT NULL,
    daily_pnl_try      numeric(19,4),
    daily_pnl_percent  numeric(19,4),
    cash_try           numeric(19,4) DEFAULT 0 NOT NULL,
    CONSTRAINT portfolio_daily_snapshots_pkey PRIMARY KEY (id)
);

ALTER SEQUENCE public.portfolio_daily_snapshots_id_seq OWNED BY public.portfolio_daily_snapshots.id;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'portfolio_daily_snapshots_portfolio_id_fkey') THEN
        ALTER TABLE public.portfolio_daily_snapshots
            ADD CONSTRAINT portfolio_daily_snapshots_portfolio_id_fkey FOREIGN KEY (portfolio_id)
            REFERENCES public.portfolios(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_portfolio_daily_snapshots_pf_date     ON public.portfolio_daily_snapshots USING btree (portfolio_id, snapshot_date);
CREATE INDEX IF NOT EXISTS idx_portfolio_daily_snapshots_pf_created  ON public.portfolio_daily_snapshots USING btree (portfolio_id, created_at);
CREATE INDEX IF NOT EXISTS idx_portfolio_daily_snapshots_pf_date_desc ON public.portfolio_daily_snapshots USING btree (portfolio_id, snapshot_date DESC);
CREATE INDEX IF NOT EXISTS idx_portfolio_daily_snapshots_snapshot_date ON public.portfolio_daily_snapshots USING btree (snapshot_date);

CREATE SEQUENCE IF NOT EXISTS public.portfolio_asset_daily_snapshot_seq
    START WITH 1 INCREMENT BY 50 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE SEQUENCE IF NOT EXISTS public.portfolio_asset_daily_snapshots_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.portfolio_asset_daily_snapshots (
    id                 bigint NOT NULL DEFAULT nextval('public.portfolio_asset_daily_snapshot_seq'::regclass),
    portfolio_id       bigint NOT NULL,
    snapshot_date      date NOT NULL,
    quantity           numeric(19,8) NOT NULL,
    unit_price_try     numeric(19,4) NOT NULL,
    market_value_try   numeric(19,4) NOT NULL,
    total_cost_try     numeric(19,4) NOT NULL,
    pnl_try            numeric(19,4) NOT NULL,
    created_at         timestamp without time zone DEFAULT now() NOT NULL,
    version            bigint DEFAULT 0 NOT NULL,
    daily_pnl_try      numeric(19,4),
    daily_pnl_percent  numeric(19,4),
    tracked_asset_id   bigint,
    asset_type         character varying(32) NOT NULL,
    asset_code         character varying(100) NOT NULL,
    CONSTRAINT portfolio_asset_daily_snapshots_pkey PRIMARY KEY (id)
);

ALTER SEQUENCE public.portfolio_asset_daily_snapshots_id_seq OWNED BY public.portfolio_asset_daily_snapshots.id;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'portfolio_asset_daily_snapshots_portfolio_id_fkey') THEN
        ALTER TABLE public.portfolio_asset_daily_snapshots
            ADD CONSTRAINT portfolio_asset_daily_snapshots_portfolio_id_fkey FOREIGN KEY (portfolio_id)
            REFERENCES public.portfolios(id) ON DELETE CASCADE;
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_portfolio_asset_snapshots_tracked_asset') THEN
        ALTER TABLE public.portfolio_asset_daily_snapshots
            ADD CONSTRAINT fk_portfolio_asset_snapshots_tracked_asset FOREIGN KEY (tracked_asset_id)
            REFERENCES public.tracked_assets(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_asset_snapshot_date                                ON public.portfolio_asset_daily_snapshots USING btree (portfolio_id, snapshot_date);
CREATE INDEX IF NOT EXISTS idx_portfolio_asset_snapshots_pf_date                  ON public.portfolio_asset_daily_snapshots USING btree (portfolio_id, snapshot_date);
CREATE INDEX IF NOT EXISTS idx_portfolio_asset_snapshots_pf_created               ON public.portfolio_asset_daily_snapshots USING btree (portfolio_id, created_at);
CREATE INDEX IF NOT EXISTS idx_portfolio_asset_snapshots_tracked_asset            ON public.portfolio_asset_daily_snapshots USING btree (tracked_asset_id);
CREATE INDEX IF NOT EXISTS idx_portfolio_asset_snapshots_pf_tracked_created       ON public.portfolio_asset_daily_snapshots USING btree (portfolio_id, tracked_asset_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_portfolio_asset_snapshots_pf_type_code             ON public.portfolio_asset_daily_snapshots USING btree (portfolio_id, asset_type, asset_code, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_portfolio_asset_snapshots_pf_type_code_created     ON public.portfolio_asset_daily_snapshots USING btree (portfolio_id, asset_type, asset_code, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_portfolio_asset_snapshots_snapshot_date            ON public.portfolio_asset_daily_snapshots USING btree (snapshot_date);
CREATE INDEX IF NOT EXISTS idx_portfolio_asset_snapshots_pf_tracked_date          ON public.portfolio_asset_daily_snapshots USING btree (portfolio_id, tracked_asset_id, snapshot_date DESC);
