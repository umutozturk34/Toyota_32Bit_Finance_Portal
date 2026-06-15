-- =====================================================================================================
-- FIXED-INCOME PORTFOLIO  (mevduat + Hazine tahvil/bono)
-- =====================================================================================================
-- Introduces the second portfolio type — FIXED — alongside the existing SPOT (spot lots + VIOP) book, and
-- the two hypothetical-lot holding tables it owns: deposits (mevduat) and bonds (tahvil/bono). This single
-- migration consolidates what was developed as a series of incremental scripts; the columns each one added
-- are folded into the table definitions here, and the development-only price-rebase experiment (a CPI scale
-- fix that turned out to be a gold-classification bug, applied then fully reverted) is dropped entirely as it
-- left no schema behind. The folded-in history, for reference:
--
--   * deposit + bond holding tables, portfolios.type column, coupon override / payment frequency,
--     per-deposit withholding rate, and the bond "quantity = adet" convention.
--
-- All statements are re-runnable (IF NOT EXISTS / pg_constraint guards) and additive: existing portfolios
-- fall to type SPOT, so spot/VIOP behaviour is unchanged with no data migration.
-- =====================================================================================================


-- ---------------------------------------------------------------------------------------------------------
-- 1. portfolios.type — SPOT (spot + VIOP) or FIXED (deposit + bond)
-- ---------------------------------------------------------------------------------------------------------
-- Set once at creation and immutable; it decides which holdings may be added and which view the frontend
-- renders. Existing rows default to SPOT and keep their current behaviour.
ALTER TABLE public.portfolios
    ADD COLUMN IF NOT EXISTS type character varying(20) DEFAULT 'SPOT' NOT NULL;

-- Postgres has no ADD CONSTRAINT IF NOT EXISTS, so guard on pg_constraint to keep the migration re-runnable.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_portfolios_type'
    ) THEN
        ALTER TABLE public.portfolios
            ADD CONSTRAINT chk_portfolios_type
                CHECK (type IN ('SPOT', 'FIXED'));
    END IF;
END $$;


-- ---------------------------------------------------------------------------------------------------------
-- 2. portfolio_deposit_holdings — a hypothetical DEPOSIT (mevduat)
-- ---------------------------------------------------------------------------------------------------------
-- `principal` is held in `currency`, accruing at a FROZEN `annual_rate` (compounded daily by
-- DepositAccrualService) from start_date to maturity_date; value freezes after maturity. There is NO tracked
-- asset and NO market price — accrual is deterministic, matching the hypothetical-lot model. Ownership flows
-- through portfolio_id -> portfolios.user_sub (no user_sub column here).
--
-- `withholding_rate` is the per-deposit stopaj as a FRACTION (e.g. 0.1500 for 15%); Türkiye deposit stopaj
-- varies by term and decree, so it is per-deposit. NULL means "use the configured default"
-- (PortfolioProperties.deposit.withholdingTaxRate).
CREATE SEQUENCE IF NOT EXISTS public.portfolio_deposit_holdings_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.portfolio_deposit_holdings (
    id               bigint NOT NULL DEFAULT nextval('public.portfolio_deposit_holdings_id_seq'::regclass),
    portfolio_id     bigint NOT NULL,
    currency         character varying(3) DEFAULT 'TRY'::character varying NOT NULL,
    principal        numeric(23,8) NOT NULL,
    annual_rate      numeric(10,4) NOT NULL,
    withholding_rate numeric(6,4),
    indicator_code   character varying(64),
    start_date       date NOT NULL,
    maturity_date    date NOT NULL,
    closed_date      date,
    closed_value_try numeric(23,8),
    created_at       timestamp without time zone DEFAULT now() NOT NULL,
    updated_at       timestamp without time zone DEFAULT now() NOT NULL,
    version          bigint DEFAULT 0 NOT NULL,
    CONSTRAINT portfolio_deposit_holdings_pkey PRIMARY KEY (id),
    CONSTRAINT fk_deposit_holding_portfolio FOREIGN KEY (portfolio_id)
        REFERENCES public.portfolios(id) ON DELETE CASCADE,
    CONSTRAINT chk_deposit_principal_positive CHECK (principal > 0),
    CONSTRAINT chk_deposit_rate_nonneg CHECK (annual_rate >= 0),
    CONSTRAINT chk_deposit_maturity_after_start CHECK (maturity_date > start_date),
    CONSTRAINT chk_deposit_withholding_rate
        CHECK (withholding_rate IS NULL OR (withholding_rate >= 0 AND withholding_rate <= 1))
);

ALTER SEQUENCE public.portfolio_deposit_holdings_id_seq OWNED BY public.portfolio_deposit_holdings.id;

CREATE INDEX IF NOT EXISTS idx_deposit_holdings_portfolio
    ON public.portfolio_deposit_holdings USING btree (portfolio_id);


-- ---------------------------------------------------------------------------------------------------------
-- 3. portfolio_bond_holdings — a hypothetical Türkiye Hazine bond/bill (tahvil/bono)
-- ---------------------------------------------------------------------------------------------------------
-- Keyed by the bond's series_code (bonds.series_code) plus a denormalized isin (bond_rate_history is keyed by
-- isin). `quantity` is the NUMBER OF BONDS (adet): one bond is one 100-nominal lot whose value IS the per-100
-- clean price, so a holding is valued price × quantity (PER UNIT) for every type — like gold. entry_price /
-- exit_price are the TRY clean price per bond. Bonds are ALWAYS TRY (never FX-converted). No FK to bonds —
-- kept decoupled, validated in-app. Ownership flows through portfolio_id -> portfolios.user_sub.
--
-- `coupon_rate_override`: the published bonds.coupon_rate is only a SUGGESTION; the user may overwrite it and
-- the override persists. Coupon is purely INFORMATIONAL/display (detail modal, schedule, chart markers) —
-- valuation uses the clean price only, so this never feeds the snapshot money pipeline. Interpreted as the
-- ANNUAL rate (per-period = annual / payments-per-year). NULL means "no override" → falls back to published.
--
-- `coupon_payment_frequency`: per-holding coupon cadence (default SEMI_ANNUAL = Türkiye Hazine standard). It
-- drives the accrued-coupon (işlemiş kupon) math: coupon dates step from issue by 12 / payments-per-year
-- months; dirty value = clean price + accrued coupon.
CREATE SEQUENCE IF NOT EXISTS public.portfolio_bond_holdings_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.portfolio_bond_holdings (
    id                       bigint NOT NULL DEFAULT nextval('public.portfolio_bond_holdings_id_seq'::regclass),
    portfolio_id             bigint NOT NULL,
    bond_series_code         character varying(50) NOT NULL,
    bond_isin                character varying(50),
    quantity                 numeric(23,8) NOT NULL,
    entry_date               date NOT NULL,
    entry_price              numeric(19,4) NOT NULL,
    exit_date                date,
    exit_price               numeric(19,4),
    coupon_rate_override     numeric(10,4),
    coupon_payment_frequency varchar(20) NOT NULL DEFAULT 'SEMI_ANNUAL',
    created_at               timestamp without time zone DEFAULT now() NOT NULL,
    updated_at               timestamp without time zone DEFAULT now() NOT NULL,
    version                  bigint DEFAULT 0 NOT NULL,
    CONSTRAINT portfolio_bond_holdings_pkey PRIMARY KEY (id),
    CONSTRAINT fk_bond_holding_portfolio FOREIGN KEY (portfolio_id)
        REFERENCES public.portfolios(id) ON DELETE CASCADE,
    CONSTRAINT chk_bond_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_bond_entry_price_positive CHECK (entry_price > 0),
    CONSTRAINT chk_bond_coupon_override_non_negative
        CHECK (coupon_rate_override IS NULL OR coupon_rate_override >= 0),
    CONSTRAINT chk_bond_coupon_payment_frequency
        CHECK (coupon_payment_frequency IN ('ANNUAL', 'SEMI_ANNUAL', 'QUARTERLY', 'MONTHLY', 'ZERO_COUPON'))
);

ALTER SEQUENCE public.portfolio_bond_holdings_id_seq OWNED BY public.portfolio_bond_holdings.id;

CREATE INDEX IF NOT EXISTS idx_bond_holdings_portfolio
    ON public.portfolio_bond_holdings USING btree (portfolio_id);
