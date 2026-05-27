
CREATE TABLE IF NOT EXISTS public.bonds (
    series_code      character varying(50) NOT NULL,
    isin_code        character varying(50),
    coupon_rate      numeric(10,4),
    simple_yield     numeric(10,4),
    base_index       numeric(19,4),
    maturity_start   date,
    maturity_end     date,
    next_coupon_date date,
    last_updated     timestamp without time zone,
    bond_type        character varying(30),
    issuer           character varying(50) DEFAULT 'HAZİNE'::character varying,
    name             character varying(255),
    image            character varying(255),
    change_amount    numeric(19,4),
    change_percent   numeric(19,4),
    asset_id         bigint NOT NULL,
    CONSTRAINT bonds_pkey PRIMARY KEY (series_code)
);

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_bonds_asset') THEN
        ALTER TABLE public.bonds ADD CONSTRAINT uq_bonds_asset UNIQUE (asset_id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_bonds_isin_code') THEN
        ALTER TABLE public.bonds ADD CONSTRAINT uq_bonds_isin_code UNIQUE (isin_code);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_bond_isin            ON public.bonds USING btree (isin_code);
CREATE INDEX IF NOT EXISTS idx_bond_type            ON public.bonds USING btree (bond_type);
CREATE INDEX IF NOT EXISTS idx_bonds_asset_id       ON public.bonds USING btree (asset_id);
CREATE INDEX IF NOT EXISTS idx_bonds_maturity_end   ON public.bonds USING btree (maturity_end);
CREATE INDEX IF NOT EXISTS idx_bonds_last_updated   ON public.bonds USING btree (last_updated DESC);

CREATE SEQUENCE IF NOT EXISTS public.bond_rate_history_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.bond_rate_history (
    id          bigint NOT NULL DEFAULT nextval('public.bond_rate_history_id_seq'::regclass),
    isin_code   character varying(50) NOT NULL,
    rate_date   date NOT NULL,
    coupon_rate numeric(10,4),
    price       numeric(14,4),
    CONSTRAINT bond_rate_history_pkey PRIMARY KEY (id)
);

ALTER SEQUENCE public.bond_rate_history_id_seq OWNED BY public.bond_rate_history.id;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'bond_rate_history_isin_code_rate_date_key') THEN
        ALTER TABLE public.bond_rate_history
            ADD CONSTRAINT bond_rate_history_isin_code_rate_date_key UNIQUE (isin_code, rate_date);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_rate_history_bond') THEN
        ALTER TABLE public.bond_rate_history
            ADD CONSTRAINT fk_rate_history_bond FOREIGN KEY (isin_code)
            REFERENCES public.bonds(isin_code) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_bond_rate_isin            ON public.bond_rate_history USING btree (isin_code);
CREATE INDEX IF NOT EXISTS idx_bond_rate_date            ON public.bond_rate_history USING btree (rate_date DESC);
CREATE INDEX IF NOT EXISTS idx_bond_rate_isin_date_desc  ON public.bond_rate_history USING btree (isin_code, rate_date DESC);
