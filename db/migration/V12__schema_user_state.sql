
CREATE TABLE IF NOT EXISTS public.user_preferences (
    user_sub              character varying(64) NOT NULL,
    theme                 character varying(16) DEFAULT 'DARK'::character varying NOT NULL,
    language              character varying(8)  DEFAULT 'tr'::character varying NOT NULL,
    timezone              character varying(32) DEFAULT 'Europe/Istanbul'::character varying NOT NULL,
    default_chart_range   character varying(8)  DEFAULT '1M'::character varying NOT NULL,
    onboarding_completed  boolean DEFAULT false NOT NULL,
    created_at            timestamp without time zone DEFAULT now() NOT NULL,
    updated_at            timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT user_preferences_pkey PRIMARY KEY (user_sub)
);

CREATE INDEX IF NOT EXISTS idx_user_preferences_theme    ON public.user_preferences USING btree (theme);
CREATE INDEX IF NOT EXISTS idx_user_preferences_language ON public.user_preferences USING btree (language);

CREATE TABLE IF NOT EXISTS public.user_layouts (
    user_sub   character varying(64) NOT NULL,
    overview   jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT user_layouts_pkey PRIMARY KEY (user_sub)
);

CREATE INDEX IF NOT EXISTS idx_user_layouts_overview_gin ON public.user_layouts USING gin (overview);
CREATE INDEX IF NOT EXISTS idx_user_layouts_updated_at   ON public.user_layouts USING btree (updated_at DESC);

CREATE SEQUENCE IF NOT EXISTS public.user_chart_preferences_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.user_chart_preferences (
    id               bigint NOT NULL DEFAULT nextval('public.user_chart_preferences_id_seq'::regclass),
    user_sub         character varying(64) NOT NULL,
    tracked_asset_id bigint NOT NULL,
    config           jsonb NOT NULL,
    created_at       timestamp with time zone DEFAULT now() NOT NULL,
    updated_at       timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT user_chart_preferences_pkey PRIMARY KEY (id)
);

ALTER SEQUENCE public.user_chart_preferences_id_seq OWNED BY public.user_chart_preferences.id;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_user_chart_preferences') THEN
        ALTER TABLE public.user_chart_preferences ADD CONSTRAINT uq_user_chart_preferences UNIQUE (user_sub, tracked_asset_id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'user_chart_preferences_tracked_asset_id_fkey') THEN
        ALTER TABLE public.user_chart_preferences
            ADD CONSTRAINT user_chart_preferences_tracked_asset_id_fkey FOREIGN KEY (tracked_asset_id)
            REFERENCES public.tracked_assets(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_user_chart_preferences_user       ON public.user_chart_preferences USING btree (user_sub);
CREATE INDEX IF NOT EXISTS idx_user_chart_preferences_tracked    ON public.user_chart_preferences USING btree (tracked_asset_id);
CREATE INDEX IF NOT EXISTS idx_user_chart_preferences_config_gin ON public.user_chart_preferences USING gin (config);
CREATE INDEX IF NOT EXISTS idx_user_chart_preferences_updated_at ON public.user_chart_preferences USING btree (updated_at DESC);

CREATE SEQUENCE IF NOT EXISTS public.user_chart_drawings_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.user_chart_drawings (
    id               bigint NOT NULL DEFAULT nextval('public.user_chart_drawings_id_seq'::regclass),
    user_sub         character varying(64) NOT NULL,
    tracked_asset_id bigint NOT NULL,
    drawings         jsonb NOT NULL,
    created_at       timestamp with time zone DEFAULT now() NOT NULL,
    updated_at       timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT user_chart_drawings_pkey PRIMARY KEY (id)
);

ALTER SEQUENCE public.user_chart_drawings_id_seq OWNED BY public.user_chart_drawings.id;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_user_chart_drawings') THEN
        ALTER TABLE public.user_chart_drawings ADD CONSTRAINT uq_user_chart_drawings UNIQUE (user_sub, tracked_asset_id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'user_chart_drawings_tracked_asset_id_fkey') THEN
        ALTER TABLE public.user_chart_drawings
            ADD CONSTRAINT user_chart_drawings_tracked_asset_id_fkey FOREIGN KEY (tracked_asset_id)
            REFERENCES public.tracked_assets(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_user_chart_drawings_user         ON public.user_chart_drawings USING btree (user_sub);
CREATE INDEX IF NOT EXISTS idx_user_chart_drawings_tracked      ON public.user_chart_drawings USING btree (tracked_asset_id);
CREATE INDEX IF NOT EXISTS idx_user_chart_drawings_drawings_gin ON public.user_chart_drawings USING gin (drawings);
CREATE INDEX IF NOT EXISTS idx_user_chart_drawings_updated_at   ON public.user_chart_drawings USING btree (updated_at DESC);

CREATE TABLE IF NOT EXISTS public.user_status (
    user_sub   character varying(64) NOT NULL,
    enabled    boolean DEFAULT true NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT user_status_pkey PRIMARY KEY (user_sub)
);

CREATE INDEX IF NOT EXISTS idx_user_status_enabled       ON public.user_status USING btree (enabled);
CREATE INDEX IF NOT EXISTS idx_user_status_disabled_only ON public.user_status USING btree (user_sub) WHERE (enabled = false);

CREATE TABLE IF NOT EXISTS public.email_change_request (
    user_sub   character varying(64) NOT NULL,
    new_email  character varying(255) NOT NULL,
    code_hash  character varying(255) NOT NULL,
    attempts   integer DEFAULT 0 NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT email_change_request_pkey PRIMARY KEY (user_sub)
);

CREATE INDEX IF NOT EXISTS idx_email_change_request_expires_at ON public.email_change_request USING btree (expires_at);
CREATE INDEX IF NOT EXISTS idx_email_change_request_new_email  ON public.email_change_request USING btree (new_email);
