
CREATE TABLE IF NOT EXISTS public.funds (
    fund_code             character varying(20)  NOT NULL,
    name                  character varying(255),
    last_updated          timestamp without time zone,
    fund_type             character varying(20),
    price                 numeric(19,6),
    bulletin_price        numeric(19,4),
    share_count           numeric(19,2),
    investor_count        numeric(19,2),
    portfolio_size        numeric(19,2),
    image                 character varying(255),
    change_percent        numeric(19,4) DEFAULT 0,
    change_amount         numeric(19,4),
    asset_id              bigint NOT NULL,
    risk_value            integer,
    sell_valor            integer,
    buyback_valor         integer,
    trade_start_time      character varying(8),
    trade_end_time        character varying(8),
    category              character varying(80),
    sub_category          character varying(80),
    category_rank         integer,
    category_total_funds  integer,
    market_share          numeric(9,4),
    isin_code             character varying(16),
    kap_link              character varying(255),
    return_1m             numeric(12,4),
    return_3m             numeric(12,4),
    return_6m             numeric(12,4),
    return_1y             numeric(12,4),
    return_ytd            numeric(12,4),
    return_3y             numeric(12,4),
    return_5y             numeric(12,4),
    CONSTRAINT funds_pkey PRIMARY KEY (fund_code)
);

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_funds_asset') THEN
        ALTER TABLE public.funds ADD CONSTRAINT uq_funds_asset UNIQUE (asset_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_funds_fund_type    ON public.funds USING btree (fund_type);
CREATE INDEX IF NOT EXISTS idx_funds_asset_id     ON public.funds USING btree (asset_id);
CREATE INDEX IF NOT EXISTS idx_funds_category     ON public.funds USING btree (category);
CREATE INDEX IF NOT EXISTS idx_funds_sub_category ON public.funds USING btree (sub_category);
CREATE INDEX IF NOT EXISTS idx_funds_isin_code    ON public.funds USING btree (isin_code);
CREATE INDEX IF NOT EXISTS idx_funds_last_updated ON public.funds USING btree (last_updated DESC);

CREATE SEQUENCE IF NOT EXISTS public.fund_candles_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.fund_candles (
    id              bigint NOT NULL DEFAULT nextval('public.fund_candles_id_seq'::regclass),
    fund_code       character varying(20) NOT NULL,
    fund_type       character varying(20),
    candle_date     timestamp without time zone NOT NULL,
    price           numeric(19,6),
    bulletin_price  numeric(19,4),
    share_count     numeric(19,2),
    investor_count  numeric(19,2),
    portfolio_size  numeric(19,2),
    CONSTRAINT fund_candles_pkey PRIMARY KEY (id)
);

ALTER SEQUENCE public.fund_candles_id_seq OWNED BY public.fund_candles.id;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uc_fund_code_date') THEN
        ALTER TABLE public.fund_candles ADD CONSTRAINT uc_fund_code_date UNIQUE (fund_code, candle_date);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_fund_candle_code') THEN
        ALTER TABLE public.fund_candles
            ADD CONSTRAINT fk_fund_candle_code FOREIGN KEY (fund_code)
            REFERENCES public.funds(fund_code) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_fund_candles_code           ON public.fund_candles USING btree (fund_code);
CREATE INDEX IF NOT EXISTS idx_fund_candles_date           ON public.fund_candles USING btree (candle_date);
CREATE INDEX IF NOT EXISTS idx_fund_candles_code_date      ON public.fund_candles USING btree (fund_code, candle_date);
CREATE INDEX IF NOT EXISTS idx_fund_candles_code_date_desc ON public.fund_candles USING btree (fund_code, candle_date DESC);

CREATE SEQUENCE IF NOT EXISTS public.fund_allocations_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.fund_allocations (
    id          bigint NOT NULL DEFAULT nextval('public.fund_allocations_id_seq'::regclass),
    fund_code   character varying(20) NOT NULL,
    asset_class character varying(16) NOT NULL,
    percentage  numeric(7,4) NOT NULL,
    as_of_date  date NOT NULL,
    CONSTRAINT fund_allocations_pkey PRIMARY KEY (id)
);

ALTER SEQUENCE public.fund_allocations_id_seq OWNED BY public.fund_allocations.id;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uc_fund_allocation') THEN
        ALTER TABLE public.fund_allocations ADD CONSTRAINT uc_fund_allocation UNIQUE (fund_code, asset_class, as_of_date);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_fund_allocation_fund') THEN
        ALTER TABLE public.fund_allocations
            ADD CONSTRAINT fk_fund_allocation_fund FOREIGN KEY (fund_code)
            REFERENCES public.funds(fund_code) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_fund_allocation_code         ON public.fund_allocations USING btree (fund_code);
CREATE INDEX IF NOT EXISTS idx_fund_allocation_asset_class  ON public.fund_allocations USING btree (asset_class);
CREATE INDEX IF NOT EXISTS idx_fund_allocation_as_of_date   ON public.fund_allocations USING btree (as_of_date DESC);
CREATE INDEX IF NOT EXISTS idx_fund_allocation_code_date    ON public.fund_allocations USING btree (fund_code, as_of_date DESC);
