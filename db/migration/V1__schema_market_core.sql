
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE TABLE IF NOT EXISTS public.stocks (
    symbol           character varying(20)  NOT NULL,
    name             character varying(255),
    image            character varying(500),
    last_updated     timestamp without time zone,
    current_price    numeric(19,4),
    previous_close   numeric(19,4),
    open_price       numeric(19,4),
    day_high         numeric(19,4),
    day_low          numeric(19,4),
    volume           bigint,
    change_percent   numeric(19,4),
    change_amount    numeric(19,4),
    exchange         character varying(50),
    currency         character varying(10),
    stock_segment    character varying(50),
    asset_id         bigint NOT NULL,
    CONSTRAINT stocks_pkey PRIMARY KEY (symbol)
);

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_stocks_asset') THEN
        ALTER TABLE public.stocks ADD CONSTRAINT uq_stocks_asset UNIQUE (asset_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_stocks_symbol      ON public.stocks USING btree (symbol);
CREATE INDEX IF NOT EXISTS idx_stocks_exchange    ON public.stocks USING btree (exchange);
CREATE INDEX IF NOT EXISTS idx_stocks_segment     ON public.stocks USING btree (stock_segment);
CREATE INDEX IF NOT EXISTS idx_stocks_asset_id    ON public.stocks USING btree (asset_id);
CREATE INDEX IF NOT EXISTS idx_stocks_last_updated ON public.stocks USING btree (last_updated DESC);

CREATE SEQUENCE IF NOT EXISTS public.stock_candles_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.stock_candles (
    id            bigint NOT NULL DEFAULT nextval('public.stock_candles_id_seq'::regclass),
    stock_symbol  character varying(20) NOT NULL,
    candle_date   timestamp without time zone NOT NULL,
    open          numeric(19,4) NOT NULL,
    high          numeric(19,4) NOT NULL,
    low           numeric(19,4) NOT NULL,
    close         numeric(19,4) NOT NULL,
    volume        bigint,
    CONSTRAINT stock_candles_pkey PRIMARY KEY (id)
);

ALTER SEQUENCE public.stock_candles_id_seq OWNED BY public.stock_candles.id;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_stock_candle') THEN
        ALTER TABLE public.stock_candles ADD CONSTRAINT uk_stock_candle UNIQUE (stock_symbol, candle_date);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_stock_candle_symbol') THEN
        ALTER TABLE public.stock_candles
            ADD CONSTRAINT fk_stock_candle_symbol FOREIGN KEY (stock_symbol)
            REFERENCES public.stocks(symbol) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_stock_candles_symbol         ON public.stock_candles USING btree (stock_symbol);
CREATE INDEX IF NOT EXISTS idx_stock_candles_date           ON public.stock_candles USING btree (candle_date);
CREATE INDEX IF NOT EXISTS idx_stock_candles_symbol_date    ON public.stock_candles USING btree (stock_symbol, candle_date);
CREATE INDEX IF NOT EXISTS idx_stock_candles_symbol_date_desc ON public.stock_candles USING btree (stock_symbol, candle_date DESC);

-- Stock detail reference data, enriched from external sources (İş Yatırım company card + index pages) and
-- reconciled by the scheduled stock refresh (never hardcoded). The company logo reuses stocks.image.
-- Company künye: one row per stock symbol (stocks.symbol, e.g. 'GARAN.IS'). Soft reference (no FK) since
-- the enrichment may run before/independently of stock discovery, so we never block on order.
CREATE TABLE IF NOT EXISTS public.company_profile (
    symbol       VARCHAR(20)  PRIMARY KEY,
    legal_name   VARCHAR(255),
    sector       VARCHAR(255),
    founded_date DATE,
    city         VARCHAR(120),
    description  TEXT,
    updated_at   TIMESTAMP    NOT NULL DEFAULT now()
);

-- Stock ↔ index membership (many-to-many) with the stock's weight in each index. stock_symbol is the tradable
-- stock (e.g. 'GARAN.IS'); index_code is the BIST index it belongs to (e.g. 'XU030'), kept bare so the UI can
-- link to the index's own stock row. Each enrichment run reconciles this set so it always mirrors the source.
CREATE TABLE IF NOT EXISTS public.stock_index_membership (
    stock_symbol VARCHAR(20)   NOT NULL,
    index_code   VARCHAR(20)   NOT NULL,
    weight       NUMERIC(9,4),
    updated_at   TIMESTAMP     NOT NULL DEFAULT now(),
    PRIMARY KEY (stock_symbol, index_code)
);
CREATE INDEX IF NOT EXISTS idx_sim_index_code ON public.stock_index_membership (index_code);

CREATE TABLE IF NOT EXISTS public.cryptos (
    id                character varying(255) NOT NULL,
    image             character varying(255),
    last_updated      timestamp(6) without time zone,
    name              character varying(255),
    symbol            character varying(255),
    change_amount     numeric(19,4),
    change_percent    numeric(19,4),
    currency          character varying(255),
    current_price     numeric(19,4),
    current_price_try numeric(19,4),
    exchange          character varying(255),
    market_cap        numeric(19,4),
    total_volume      numeric(19,4),
    asset_id          bigint NOT NULL,
    CONSTRAINT cryptos_pkey PRIMARY KEY (id)
);

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_cryptos_asset') THEN
        ALTER TABLE public.cryptos ADD CONSTRAINT uq_cryptos_asset UNIQUE (asset_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_crypto_symbol         ON public.cryptos USING btree (symbol);
CREATE INDEX IF NOT EXISTS idx_cryptos_asset_id      ON public.cryptos USING btree (asset_id);
CREATE INDEX IF NOT EXISTS idx_cryptos_last_updated  ON public.cryptos USING btree (last_updated DESC);

CREATE TABLE IF NOT EXISTS public.crypto_candles (
    id          bigint GENERATED BY DEFAULT AS IDENTITY (
        SEQUENCE NAME public.crypto_candles_id_seq
        START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1
    ) NOT NULL,
    candle_date timestamp(6) without time zone NOT NULL,
    close       numeric(19,4),
    high        numeric(19,4),
    low         numeric(19,4),
    open        numeric(19,4),
    crypto_id   character varying(255),
    volume      bigint,
    CONSTRAINT crypto_candles_pkey PRIMARY KEY (id)
);

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uc_crypto_id_candle_date') THEN
        ALTER TABLE public.crypto_candles ADD CONSTRAINT uc_crypto_id_candle_date UNIQUE (crypto_id, candle_date);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_crypto_candle_id') THEN
        ALTER TABLE public.crypto_candles
            ADD CONSTRAINT fk_crypto_candle_id FOREIGN KEY (crypto_id)
            REFERENCES public.cryptos(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_crypto_candles_crypto_id          ON public.crypto_candles USING btree (crypto_id);
CREATE INDEX IF NOT EXISTS idx_crypto_candles_date               ON public.crypto_candles USING btree (candle_date);
CREATE INDEX IF NOT EXISTS idx_crypto_candles_crypto_date        ON public.crypto_candles USING btree (crypto_id, candle_date);
CREATE INDEX IF NOT EXISTS idx_crypto_candles_crypto_date_desc   ON public.crypto_candles USING btree (crypto_id, candle_date DESC);

CREATE TABLE IF NOT EXISTS public.forex (
    currency_code           character varying(10) NOT NULL,
    selling_price           numeric(19,4),
    image                   character varying(255),
    buying_price            numeric(19,4),
    effective_buying_price  numeric(19,4),
    effective_selling_price numeric(19,4),
    name                    character varying(255),
    last_updated            timestamp(6) without time zone,
    change_amount           numeric(19,4),
    change_percent          numeric(19,4),
    asset_id                bigint,
    CONSTRAINT forex_pkey PRIMARY KEY (currency_code)
);

CREATE INDEX IF NOT EXISTS idx_forex_asset_id      ON public.forex USING btree (asset_id);
CREATE INDEX IF NOT EXISTS idx_forex_last_updated  ON public.forex USING btree (last_updated DESC);

CREATE SEQUENCE IF NOT EXISTS public.forex_candles_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.forex_candles (
    id                      bigint NOT NULL DEFAULT nextval('public.forex_candles_id_seq'::regclass),
    currency_code           character varying(10) NOT NULL,
    candle_date             timestamp(6) without time zone NOT NULL,
    created_at              timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at              timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP,
    selling_price           numeric(19,4),
    buying_price            numeric(19,4),
    effective_buying_price  numeric(19,4),
    effective_selling_price numeric(19,4),
    CONSTRAINT forex_candles_pkey PRIMARY KEY (id)
);

ALTER SEQUENCE public.forex_candles_id_seq OWNED BY public.forex_candles.id;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_forex_candle_currency_date') THEN
        ALTER TABLE public.forex_candles ADD CONSTRAINT uk_forex_candle_currency_date UNIQUE (currency_code, candle_date);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_forex_candle_currency') THEN
        ALTER TABLE public.forex_candles
            ADD CONSTRAINT fk_forex_candle_currency FOREIGN KEY (currency_code)
            REFERENCES public.forex(currency_code) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_forex_candle_currency       ON public.forex_candles USING btree (currency_code);
CREATE INDEX IF NOT EXISTS idx_forex_candle_date           ON public.forex_candles USING btree (candle_date);
CREATE INDEX IF NOT EXISTS idx_forex_candle_currency_date  ON public.forex_candles USING btree (currency_code, candle_date);
CREATE INDEX IF NOT EXISTS idx_forex_candle_currency_date_desc ON public.forex_candles USING btree (currency_code, candle_date DESC);
