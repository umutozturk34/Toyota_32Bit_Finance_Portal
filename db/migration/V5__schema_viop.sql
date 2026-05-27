
CREATE SEQUENCE IF NOT EXISTS public.viop_contracts_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.viop_contracts (
    id                bigint NOT NULL DEFAULT nextval('public.viop_contracts_id_seq'::regclass),
    asset_id          bigint,
    symbol            character varying(64) NOT NULL,
    name              character varying(255),
    image             character varying(255),
    last_updated      timestamp without time zone,
    change_amount     numeric(19,4),
    change_percent    numeric(19,4),
    kind              character varying(16) NOT NULL,
    category          character varying(32),
    underlying        character varying(64),
    expiry_date       date,
    contract_size     numeric(19,8),
    initial_margin    numeric(19,4),
    settlement_type   character varying(32),
    currency          character varying(8),
    tick_size         numeric(19,8),
    option_side       character varying(8),
    strike_price      numeric(19,4),
    exercise_style    character varying(16),
    last_price        numeric(19,4),
    day_close         numeric(19,4),
    bid               numeric(19,4),
    ask               numeric(19,4),
    open_price        numeric(19,4),
    day_high          numeric(19,4),
    day_low           numeric(19,4),
    volume_lot        numeric(19,4),
    volume_try        numeric(19,4),
    settlement_price  numeric(19,4),
    active            boolean DEFAULT true NOT NULL,
    display_name      character varying(128),
    CONSTRAINT viop_contracts_pkey PRIMARY KEY (id),
    CONSTRAINT chk_viop_kind CHECK (((kind)::text = ANY ((ARRAY['FUTURE'::character varying, 'OPTION'::character varying])::text[]))),
    CONSTRAINT chk_viop_option_side CHECK (((option_side IS NULL) OR ((option_side)::text = ANY ((ARRAY['CALL'::character varying, 'PUT'::character varying])::text[])))),
    CONSTRAINT chk_viop_exercise CHECK (((exercise_style IS NULL) OR ((exercise_style)::text = ANY ((ARRAY['EUROPEAN'::character varying, 'AMERICAN'::character varying])::text[])))),
    CONSTRAINT chk_viop_contract_size_positive CHECK (((contract_size IS NULL) OR (contract_size > (0)::numeric))),
    CONSTRAINT chk_viop_option_fields CHECK (((((kind)::text = 'FUTURE'::text) AND (option_side IS NULL) AND (strike_price IS NULL) AND (exercise_style IS NULL)) OR (((kind)::text = 'OPTION'::text) AND (option_side IS NOT NULL))))
);

ALTER SEQUENCE public.viop_contracts_id_seq OWNED BY public.viop_contracts.id;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uc_viop_contracts_symbol') THEN
        ALTER TABLE public.viop_contracts ADD CONSTRAINT uc_viop_contracts_symbol UNIQUE (symbol);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_viop_contracts_kind          ON public.viop_contracts USING btree (kind);
CREATE INDEX IF NOT EXISTS idx_viop_contracts_category      ON public.viop_contracts USING btree (category);
CREATE INDEX IF NOT EXISTS idx_viop_contracts_underlying    ON public.viop_contracts USING btree (underlying);
CREATE INDEX IF NOT EXISTS idx_viop_contracts_active_expiry ON public.viop_contracts USING btree (active, expiry_date);
CREATE INDEX IF NOT EXISTS idx_viop_contracts_display_name  ON public.viop_contracts USING btree (lower((display_name)::text));
CREATE INDEX IF NOT EXISTS idx_viop_contracts_asset_id      ON public.viop_contracts USING btree (asset_id);
CREATE INDEX IF NOT EXISTS idx_viop_contracts_expiry_date   ON public.viop_contracts USING btree (expiry_date);
CREATE INDEX IF NOT EXISTS idx_viop_contracts_last_updated  ON public.viop_contracts USING btree (last_updated DESC);

CREATE SEQUENCE IF NOT EXISTS public.viop_candles_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.viop_candles (
    id          bigint NOT NULL DEFAULT nextval('public.viop_candles_id_seq'::regclass),
    symbol      character varying(64) NOT NULL,
    candle_date timestamp without time zone NOT NULL,
    close       numeric(19,4) NOT NULL,
    created_at  timestamp without time zone DEFAULT now() NOT NULL,
    updated_at  timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT viop_candles_pkey PRIMARY KEY (id)
);

ALTER SEQUENCE public.viop_candles_id_seq OWNED BY public.viop_candles.id;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uc_viop_candle_symbol_date') THEN
        ALTER TABLE public.viop_candles ADD CONSTRAINT uc_viop_candle_symbol_date UNIQUE (symbol, candle_date);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_viop_candle_symbol') THEN
        ALTER TABLE public.viop_candles
            ADD CONSTRAINT fk_viop_candle_symbol FOREIGN KEY (symbol)
            REFERENCES public.viop_contracts(symbol) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_viop_candle_symbol           ON public.viop_candles USING btree (symbol);
CREATE INDEX IF NOT EXISTS idx_viop_candle_date             ON public.viop_candles USING btree (candle_date);
CREATE INDEX IF NOT EXISTS idx_viop_candle_symbol_date      ON public.viop_candles USING btree (symbol, candle_date);
CREATE INDEX IF NOT EXISTS idx_viop_candle_symbol_date_desc ON public.viop_candles USING btree (symbol, candle_date DESC);
