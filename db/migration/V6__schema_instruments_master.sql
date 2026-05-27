
CREATE SEQUENCE IF NOT EXISTS public.assets_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.instruments (
    id              bigint NOT NULL DEFAULT nextval('public.assets_id_seq'::regclass),
    instrument_type character varying(16) NOT NULL,
    asset_code      character varying(100) NOT NULL,
    active          boolean DEFAULT true NOT NULL,
    created_at      timestamp without time zone DEFAULT now() NOT NULL,
    updated_at      timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT assets_pkey PRIMARY KEY (id),
    CONSTRAINT chk_instruments_instrument_type CHECK (((instrument_type)::text = ANY ((ARRAY[
        'STOCK'::character varying, 'CRYPTO'::character varying, 'FOREX'::character varying,
        'FUND'::character varying, 'BOND'::character varying, 'COMMODITY'::character varying,
        'VIOP'::character varying, 'MACRO_RATE'::character varying,
        'MACRO_INFLATION'::character varying, 'MACRO_DEPOSIT'::character varying
    ])::text[])))
);

ALTER SEQUENCE public.assets_id_seq OWNED BY public.instruments.id;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uc_instruments_type_code') THEN
        ALTER TABLE public.instruments ADD CONSTRAINT uc_instruments_type_code UNIQUE (instrument_type, asset_code);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_instruments_instrument_type ON public.instruments USING btree (instrument_type);
CREATE INDEX IF NOT EXISTS idx_instruments_active          ON public.instruments USING btree (active);
CREATE INDEX IF NOT EXISTS idx_instruments_active_type     ON public.instruments USING btree (instrument_type) WHERE (active = true);
CREATE INDEX IF NOT EXISTS idx_instruments_asset_code      ON public.instruments USING btree (asset_code);

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_stocks_asset') THEN
        ALTER TABLE public.stocks
            ADD CONSTRAINT fk_stocks_asset FOREIGN KEY (asset_id)
            REFERENCES public.instruments(id) ON DELETE CASCADE;
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_cryptos_asset') THEN
        ALTER TABLE public.cryptos
            ADD CONSTRAINT fk_cryptos_asset FOREIGN KEY (asset_id)
            REFERENCES public.instruments(id) ON DELETE CASCADE;
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_forex_asset') THEN
        ALTER TABLE public.forex
            ADD CONSTRAINT fk_forex_asset FOREIGN KEY (asset_id)
            REFERENCES public.instruments(id) ON DELETE SET NULL;
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_funds_asset') THEN
        ALTER TABLE public.funds
            ADD CONSTRAINT fk_funds_asset FOREIGN KEY (asset_id)
            REFERENCES public.instruments(id) ON DELETE CASCADE;
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_bonds_asset') THEN
        ALTER TABLE public.bonds
            ADD CONSTRAINT fk_bonds_asset FOREIGN KEY (asset_id)
            REFERENCES public.instruments(id) ON DELETE CASCADE;
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_commodities_asset') THEN
        ALTER TABLE public.commodities
            ADD CONSTRAINT fk_commodities_asset FOREIGN KEY (asset_id)
            REFERENCES public.instruments(id) ON DELETE CASCADE;
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_viop_contracts_asset') THEN
        ALTER TABLE public.viop_contracts
            ADD CONSTRAINT fk_viop_contracts_asset FOREIGN KEY (asset_id)
            REFERENCES public.instruments(id) ON DELETE CASCADE;
    END IF;
END $$;
