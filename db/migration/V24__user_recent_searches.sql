CREATE TABLE IF NOT EXISTS public.user_recent_searches (
    user_sub   character varying(64) NOT NULL,
    items      jsonb DEFAULT '[]'::jsonb NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT user_recent_searches_pkey PRIMARY KEY (user_sub)
);

CREATE INDEX IF NOT EXISTS idx_user_recent_searches_updated_at
    ON public.user_recent_searches USING btree (updated_at DESC);
