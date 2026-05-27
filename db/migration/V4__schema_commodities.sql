
CREATE TABLE IF NOT EXISTS public.commodities (
    commodity_code      character varying(30) NOT NULL,
    commodity_name      character varying(255),
    commodity_name_tr   character varying(255),
    current_price       numeric(19,4),
    change_amount       numeric(19,4),
    change_percent      numeric(19,4),
    unit                character varying(30),
    yahoo_updated_at    timestamp without time zone,
    name                character varying(255),
    image               text,
    last_updated        timestamp without time zone,
    created_at          timestamp without time zone DEFAULT now() NOT NULL,
    updated_at          timestamp without time zone DEFAULT now() NOT NULL,
    current_price_usd   numeric(19,4),
    previous_price_usd  numeric(19,4),
    open_price          numeric(19,4),
    day_high            numeric(19,4),
    day_low             numeric(19,4),
    volume              bigint,
    commodity_segment   character varying(20),
    yahoo_symbol        character varying(20),
    asset_id            bigint NOT NULL,
    CONSTRAINT commodities_pkey PRIMARY KEY (commodity_code)
);

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_commodities_asset') THEN
        ALTER TABLE public.commodities ADD CONSTRAINT uq_commodities_asset UNIQUE (asset_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_commodities_segment       ON public.commodities USING btree (commodity_segment);
CREATE INDEX IF NOT EXISTS idx_commodities_asset_id      ON public.commodities USING btree (asset_id);
CREATE INDEX IF NOT EXISTS idx_commodities_yahoo_symbol  ON public.commodities USING btree (yahoo_symbol);
CREATE INDEX IF NOT EXISTS idx_commodities_last_updated  ON public.commodities USING btree (last_updated DESC);

CREATE SEQUENCE IF NOT EXISTS public.commodity_candles_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.commodity_candles (
    id              bigint NOT NULL DEFAULT nextval('public.commodity_candles_id_seq'::regclass),
    commodity_code  character varying(30) NOT NULL,
    candle_date     timestamp without time zone NOT NULL,
    open            numeric(19,4) NOT NULL,
    high            numeric(19,4) NOT NULL,
    low             numeric(19,4) NOT NULL,
    close           numeric(19,4) NOT NULL,
    created_at      timestamp without time zone DEFAULT now() NOT NULL,
    updated_at      timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT commodity_candles_pkey PRIMARY KEY (id)
);

ALTER SEQUENCE public.commodity_candles_id_seq OWNED BY public.commodity_candles.id;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uc_commodity_code_date') THEN
        ALTER TABLE public.commodity_candles ADD CONSTRAINT uc_commodity_code_date UNIQUE (commodity_code, candle_date);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'commodity_candles_commodity_code_fkey') THEN
        ALTER TABLE public.commodity_candles
            ADD CONSTRAINT commodity_candles_commodity_code_fkey FOREIGN KEY (commodity_code)
            REFERENCES public.commodities(commodity_code) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_commodity_candle_code           ON public.commodity_candles USING btree (commodity_code);
CREATE INDEX IF NOT EXISTS idx_commodity_candle_date           ON public.commodity_candles USING btree (candle_date);
CREATE INDEX IF NOT EXISTS idx_commodity_candle_code_date      ON public.commodity_candles USING btree (commodity_code, candle_date);
CREATE INDEX IF NOT EXISTS idx_commodity_candle_code_date_desc ON public.commodity_candles USING btree (commodity_code, candle_date DESC);
