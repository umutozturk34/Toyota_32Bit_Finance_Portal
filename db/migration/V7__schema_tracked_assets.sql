
CREATE SEQUENCE IF NOT EXISTS public.tracked_assets_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.tracked_assets (
    id              bigint NOT NULL DEFAULT nextval('public.tracked_assets_id_seq'::regclass),
    asset_type      character varying(30) NOT NULL,
    asset_code      character varying(100) NOT NULL,
    display_name    character varying(255),
    stock_segment   character varying(30),
    sort_order      integer DEFAULT 0 NOT NULL,
    created_at      timestamp without time zone DEFAULT now() NOT NULL,
    updated_at      timestamp without time zone DEFAULT now() NOT NULL,
    binance_symbol  character varying(100),
    index_asset     boolean DEFAULT false NOT NULL,
    compare_only    boolean DEFAULT false NOT NULL,
    asset_id        bigint NOT NULL,
    enabled         boolean DEFAULT true NOT NULL,
    CONSTRAINT tracked_assets_pkey PRIMARY KEY (id)
);

ALTER SEQUENCE public.tracked_assets_id_seq OWNED BY public.tracked_assets.id;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_tracked_assets_asset') THEN
        ALTER TABLE public.tracked_assets ADD CONSTRAINT uq_tracked_assets_asset UNIQUE (asset_id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_tracked_assets_asset') THEN
        ALTER TABLE public.tracked_assets
            ADD CONSTRAINT fk_tracked_assets_asset FOREIGN KEY (asset_id)
            REFERENCES public.instruments(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uc_tracked_assets_type_code     ON public.tracked_assets USING btree (asset_type, asset_code);
CREATE INDEX IF NOT EXISTS idx_tracked_assets_type_enabled        ON public.tracked_assets USING btree (asset_type, enabled);
CREATE INDEX IF NOT EXISTS idx_tracked_assets_type_sort           ON public.tracked_assets USING btree (asset_type, sort_order);
CREATE INDEX IF NOT EXISTS idx_tracked_assets_enabled             ON public.tracked_assets USING btree (enabled) WHERE (enabled = true);
CREATE INDEX IF NOT EXISTS idx_tracked_assets_index_asset         ON public.tracked_assets USING btree (index_asset) WHERE (index_asset = true);
CREATE INDEX IF NOT EXISTS idx_tracked_assets_compare_only        ON public.tracked_assets USING btree (compare_only) WHERE (compare_only = true);
CREATE INDEX IF NOT EXISTS idx_tracked_assets_binance_symbol      ON public.tracked_assets USING btree (binance_symbol);
CREATE INDEX IF NOT EXISTS idx_tracked_assets_asset_id            ON public.tracked_assets USING btree (asset_id);
CREATE INDEX IF NOT EXISTS idx_tracked_assets_stock_segment       ON public.tracked_assets USING btree (stock_segment);
