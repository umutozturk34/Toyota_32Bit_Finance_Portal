
CREATE SEQUENCE IF NOT EXISTS public.portfolios_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.portfolios (
    id         bigint NOT NULL DEFAULT nextval('public.portfolios_id_seq'::regclass),
    user_sub   character varying(255) NOT NULL,
    name       character varying(100) DEFAULT 'Ana Portföy'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    version    bigint DEFAULT 0 NOT NULL,
    CONSTRAINT portfolios_pkey PRIMARY KEY (id)
);

ALTER SEQUENCE public.portfolios_id_seq OWNED BY public.portfolios.id;

CREATE UNIQUE INDEX IF NOT EXISTS uc_portfolio_user_name ON public.portfolios USING btree (user_sub, name);
CREATE INDEX IF NOT EXISTS idx_portfolio_user_sub        ON public.portfolios USING btree (user_sub);
CREATE INDEX IF NOT EXISTS idx_portfolios_user_sub       ON public.portfolios USING btree (user_sub);
CREATE INDEX IF NOT EXISTS idx_portfolios_created_at     ON public.portfolios USING btree (created_at DESC);

CREATE SEQUENCE IF NOT EXISTS public.portfolio_positions_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.portfolio_positions (
    id               bigint NOT NULL DEFAULT nextval('public.portfolio_positions_id_seq'::regclass),
    portfolio_id     bigint NOT NULL,
    quantity         numeric(19,8) DEFAULT 0 NOT NULL,
    created_at       timestamp without time zone DEFAULT now() NOT NULL,
    updated_at       timestamp without time zone DEFAULT now() NOT NULL,
    entry_date       timestamp without time zone NOT NULL,
    entry_price      numeric(19,4) NOT NULL,
    version          bigint DEFAULT 0 NOT NULL,
    tracked_asset_id bigint NOT NULL,
    exit_date        timestamp without time zone,
    exit_price       numeric(19,4),
    CONSTRAINT portfolio_positions_pkey PRIMARY KEY (id)
);

ALTER SEQUENCE public.portfolio_positions_id_seq OWNED BY public.portfolio_positions.id;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'portfolio_positions_portfolio_id_fkey') THEN
        ALTER TABLE public.portfolio_positions
            ADD CONSTRAINT portfolio_positions_portfolio_id_fkey FOREIGN KEY (portfolio_id)
            REFERENCES public.portfolios(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_portfolio_positions_tracked_asset') THEN
        ALTER TABLE public.portfolio_positions
            ADD CONSTRAINT fk_portfolio_positions_tracked_asset FOREIGN KEY (tracked_asset_id)
            REFERENCES public.tracked_assets(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_portfolio_positions_portfolio       ON public.portfolio_positions USING btree (portfolio_id);
CREATE INDEX IF NOT EXISTS idx_portfolio_positions_tracked_asset   ON public.portfolio_positions USING btree (tracked_asset_id);
CREATE INDEX IF NOT EXISTS idx_portfolio_positions_exit_date       ON public.portfolio_positions USING btree (exit_date) WHERE (exit_date IS NOT NULL);
CREATE INDEX IF NOT EXISTS idx_portfolio_positions_open            ON public.portfolio_positions USING btree (portfolio_id) WHERE (exit_date IS NULL);
CREATE INDEX IF NOT EXISTS idx_portfolio_positions_pf_tracked      ON public.portfolio_positions USING btree (portfolio_id, tracked_asset_id);
CREATE INDEX IF NOT EXISTS idx_portfolio_positions_entry_date      ON public.portfolio_positions USING btree (entry_date DESC);

CREATE SEQUENCE IF NOT EXISTS public.portfolio_derivative_positions_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.portfolio_derivative_positions (
    id                bigint NOT NULL DEFAULT nextval('public.portfolio_derivative_positions_id_seq'::regclass),
    portfolio_id      bigint NOT NULL,
    viop_contract_id  bigint NOT NULL,
    direction         character varying(8) NOT NULL,
    entry_date        date NOT NULL,
    entry_price       numeric(19,4) NOT NULL,
    quantity_lot      numeric(19,4) NOT NULL,
    close_date        date,
    close_price       numeric(19,4),
    close_reason      character varying(16),
    created_at        timestamp without time zone NOT NULL,
    updated_at        timestamp without time zone NOT NULL,
    version           bigint DEFAULT 0 NOT NULL,
    CONSTRAINT portfolio_derivative_positions_pkey PRIMARY KEY (id),
    CONSTRAINT chk_pdp_direction CHECK (((direction)::text = ANY ((ARRAY['LONG'::character varying, 'SHORT'::character varying])::text[]))),
    CONSTRAINT chk_pdp_quantity_positive CHECK ((quantity_lot > (0)::numeric)),
    CONSTRAINT chk_pdp_close_after_entry CHECK (((close_date IS NULL) OR (close_date >= entry_date))),
    CONSTRAINT chk_pdp_close_reason CHECK (((close_reason IS NULL) OR ((close_reason)::text = ANY ((ARRAY['USER_CLOSED'::character varying, 'EXPIRED'::character varying])::text[])))),
    CONSTRAINT chk_pdp_close CHECK ((((close_date IS NULL) AND (close_price IS NULL) AND (close_reason IS NULL)) OR ((close_date IS NOT NULL) AND (close_price IS NOT NULL) AND (close_reason IS NOT NULL))))
);

ALTER SEQUENCE public.portfolio_derivative_positions_id_seq OWNED BY public.portfolio_derivative_positions.id;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_pdp_portfolio') THEN
        ALTER TABLE public.portfolio_derivative_positions
            ADD CONSTRAINT fk_pdp_portfolio FOREIGN KEY (portfolio_id)
            REFERENCES public.portfolios(id) ON DELETE CASCADE;
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_pdp_viop_contract') THEN
        ALTER TABLE public.portfolio_derivative_positions
            ADD CONSTRAINT fk_pdp_viop_contract FOREIGN KEY (viop_contract_id)
            REFERENCES public.viop_contracts(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_pdp_portfolio          ON public.portfolio_derivative_positions USING btree (portfolio_id);
CREATE INDEX IF NOT EXISTS idx_pdp_contract           ON public.portfolio_derivative_positions USING btree (viop_contract_id);
CREATE INDEX IF NOT EXISTS idx_pdp_open               ON public.portfolio_derivative_positions USING btree (portfolio_id, close_date);
CREATE INDEX IF NOT EXISTS idx_pdp_open_only          ON public.portfolio_derivative_positions USING btree (portfolio_id) WHERE (close_date IS NULL);
CREATE INDEX IF NOT EXISTS idx_pdp_close_date         ON public.portfolio_derivative_positions USING btree (close_date) WHERE (close_date IS NOT NULL);
CREATE INDEX IF NOT EXISTS idx_pdp_pf_contract        ON public.portfolio_derivative_positions USING btree (portfolio_id, viop_contract_id);
CREATE INDEX IF NOT EXISTS idx_pdp_entry_date         ON public.portfolio_derivative_positions USING btree (entry_date DESC);
