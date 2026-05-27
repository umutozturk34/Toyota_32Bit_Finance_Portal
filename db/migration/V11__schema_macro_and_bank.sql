
CREATE SEQUENCE IF NOT EXISTS public.macro_indicators_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.macro_indicators (
    id            bigint NOT NULL DEFAULT nextval('public.macro_indicators_id_seq'::regclass),
    instrument_id bigint NOT NULL,
    code          character varying(64) NOT NULL,
    label         character varying(64) NOT NULL,
    category      character varying(16) NOT NULL,
    unit          character varying(16) NOT NULL,
    frequency     character varying(16) NOT NULL,
    currency      character varying(8),
    maturity      character varying(16),
    prominent     boolean DEFAULT false NOT NULL,
    last_value    numeric(19,6),
    last_date     date,
    created_at    timestamp without time zone DEFAULT now() NOT NULL,
    updated_at    timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT macro_indicators_pkey PRIMARY KEY (id),
    CONSTRAINT chk_macro_indicators_category CHECK (((category)::text = ANY ((ARRAY['RATES'::character varying, 'INFLATION'::character varying, 'DEPOSIT'::character varying])::text[]))),
    CONSTRAINT chk_macro_indicators_unit CHECK (((unit)::text = ANY ((ARRAY['PERCENT'::character varying, 'INDEX'::character varying, 'NUMBER'::character varying])::text[]))),
    CONSTRAINT chk_macro_indicators_frequency CHECK (((frequency)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'MONTHLY'::character varying])::text[])))
);

ALTER SEQUENCE public.macro_indicators_id_seq OWNED BY public.macro_indicators.id;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'macro_indicators_code_key') THEN
        ALTER TABLE public.macro_indicators ADD CONSTRAINT macro_indicators_code_key UNIQUE (code);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'macro_indicators_instrument_id_key') THEN
        ALTER TABLE public.macro_indicators ADD CONSTRAINT macro_indicators_instrument_id_key UNIQUE (instrument_id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_macro_indicators_instrument') THEN
        ALTER TABLE public.macro_indicators
            ADD CONSTRAINT fk_macro_indicators_instrument FOREIGN KEY (instrument_id)
            REFERENCES public.instruments(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_macro_indicators_category    ON public.macro_indicators USING btree (category);
CREATE INDEX IF NOT EXISTS idx_macro_indicators_prominent   ON public.macro_indicators USING btree (prominent) WHERE (prominent = true);
CREATE INDEX IF NOT EXISTS idx_macro_indicators_currency    ON public.macro_indicators USING btree (currency);
CREATE INDEX IF NOT EXISTS idx_macro_indicators_frequency   ON public.macro_indicators USING btree (frequency);
CREATE INDEX IF NOT EXISTS idx_macro_indicators_last_date   ON public.macro_indicators USING btree (last_date DESC);
CREATE INDEX IF NOT EXISTS idx_macro_indicators_instrument  ON public.macro_indicators USING btree (instrument_id);

CREATE SEQUENCE IF NOT EXISTS public.macro_indicator_points_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.macro_indicator_points (
    id           bigint NOT NULL DEFAULT nextval('public.macro_indicator_points_id_seq'::regclass),
    indicator_id bigint NOT NULL,
    observed_at  date NOT NULL,
    value        numeric(19,6) NOT NULL,
    created_at   timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT macro_indicator_points_pkey PRIMARY KEY (id)
);

ALTER SEQUENCE public.macro_indicator_points_id_seq OWNED BY public.macro_indicator_points.id;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uc_macro_points_indicator_date') THEN
        ALTER TABLE public.macro_indicator_points ADD CONSTRAINT uc_macro_points_indicator_date UNIQUE (indicator_id, observed_at);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_macro_points_indicator') THEN
        ALTER TABLE public.macro_indicator_points
            ADD CONSTRAINT fk_macro_points_indicator FOREIGN KEY (indicator_id)
            REFERENCES public.macro_indicators(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_macro_points_indicator_date       ON public.macro_indicator_points USING btree (indicator_id, observed_at DESC);
CREATE INDEX IF NOT EXISTS idx_macro_points_indicator            ON public.macro_indicator_points USING btree (indicator_id);
CREATE INDEX IF NOT EXISTS idx_macro_points_observed_at          ON public.macro_indicator_points USING btree (observed_at DESC);

CREATE SEQUENCE IF NOT EXISTS public.bank_exchange_rates_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.bank_exchange_rates (
    id              bigint NOT NULL DEFAULT nextval('public.bank_exchange_rates_id_seq'::regclass),
    source          character varying(32) NOT NULL,
    bank_code       character varying(64) NOT NULL,
    bank_name       character varying(128) NOT NULL,
    bank_logo_url   character varying(512),
    currency_code   character varying(32) NOT NULL,
    currency_name   character varying(64),
    asset_kind      character varying(16) NOT NULL,
    buy_rate        numeric(19,4),
    sell_rate       numeric(19,4),
    captured_at     timestamp without time zone NOT NULL,
    created_at      timestamp without time zone DEFAULT now() NOT NULL,
    updated_at      timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT bank_exchange_rates_pkey PRIMARY KEY (id)
);

ALTER SEQUENCE public.bank_exchange_rates_id_seq OWNED BY public.bank_exchange_rates.id;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_bank_rate') THEN
        ALTER TABLE public.bank_exchange_rates ADD CONSTRAINT uq_bank_rate UNIQUE (source, bank_code, currency_code);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_bank_rate_bank             ON public.bank_exchange_rates USING btree (bank_code);
CREATE INDEX IF NOT EXISTS idx_bank_rate_currency         ON public.bank_exchange_rates USING btree (currency_code);
CREATE INDEX IF NOT EXISTS idx_bank_rate_asset_kind       ON public.bank_exchange_rates USING btree (asset_kind);
CREATE INDEX IF NOT EXISTS idx_bank_rate_captured_at      ON public.bank_exchange_rates USING btree (captured_at DESC);
CREATE INDEX IF NOT EXISTS idx_bank_rate_source           ON public.bank_exchange_rates USING btree (source);
CREATE INDEX IF NOT EXISTS idx_bank_rate_currency_kind    ON public.bank_exchange_rates USING btree (currency_code, asset_kind);
CREATE INDEX IF NOT EXISTS idx_bank_rate_bank_currency    ON public.bank_exchange_rates USING btree (bank_code, currency_code);
