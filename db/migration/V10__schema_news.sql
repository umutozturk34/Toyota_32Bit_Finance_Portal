
CREATE TABLE IF NOT EXISTS public.news_sources (
    id                bigint GENERATED ALWAYS AS IDENTITY (
        SEQUENCE NAME public.news_sources_id_seq
        START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1
    ) NOT NULL,
    name              character varying(100) NOT NULL,
    url               character varying(1024) NOT NULL,
    source_type       character varying(30) DEFAULT 'RSS'::character varying NOT NULL,
    default_category  character varying(30),
    enabled           boolean DEFAULT true NOT NULL,
    sort_order        integer DEFAULT 0 NOT NULL,
    created_at        timestamp without time zone DEFAULT now() NOT NULL,
    updated_at        timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT news_sources_pkey PRIMARY KEY (id)
);

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uc_news_sources_name') THEN
        ALTER TABLE public.news_sources ADD CONSTRAINT uc_news_sources_name UNIQUE (name);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_news_sources_enabled       ON public.news_sources USING btree (enabled);
CREATE INDEX IF NOT EXISTS idx_news_sources_enabled_only  ON public.news_sources USING btree (sort_order) WHERE (enabled = true);
CREATE INDEX IF NOT EXISTS idx_news_sources_sort_order    ON public.news_sources USING btree (sort_order);
CREATE INDEX IF NOT EXISTS idx_news_sources_source_type   ON public.news_sources USING btree (source_type);

CREATE SEQUENCE IF NOT EXISTS public.news_articles_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.news_articles (
    id            bigint NOT NULL DEFAULT nextval('public.news_articles_id_seq'::regclass),
    link          character varying(1024) NOT NULL,
    title         character varying(500) NOT NULL,
    description   text,
    category      character varying(30) NOT NULL,
    published_at  timestamp without time zone NOT NULL,
    fetched_at    timestamp without time zone NOT NULL,
    image_url     character varying(1024),
    content       text,
    guid          character varying(1024),
    source_id     bigint NOT NULL,
    CONSTRAINT news_articles_pkey PRIMARY KEY (id)
);

ALTER SEQUENCE public.news_articles_id_seq OWNED BY public.news_articles.id;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'news_articles_link_key') THEN
        ALTER TABLE public.news_articles ADD CONSTRAINT news_articles_link_key UNIQUE (link);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_news_articles_source') THEN
        ALTER TABLE public.news_articles
            ADD CONSTRAINT fk_news_articles_source FOREIGN KEY (source_id)
            REFERENCES public.news_sources(id) ON DELETE RESTRICT;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS idx_news_guid                ON public.news_articles USING btree (guid) WHERE (guid IS NOT NULL);
CREATE INDEX IF NOT EXISTS idx_news_articles_source            ON public.news_articles USING btree (source_id);
CREATE INDEX IF NOT EXISTS idx_news_category                   ON public.news_articles USING btree (category);
CREATE INDEX IF NOT EXISTS idx_news_published_at               ON public.news_articles USING btree (published_at);
CREATE INDEX IF NOT EXISTS idx_news_published_at_desc          ON public.news_articles USING btree (published_at DESC);
CREATE INDEX IF NOT EXISTS idx_news_articles_category_published ON public.news_articles USING btree (category, published_at DESC);
CREATE INDEX IF NOT EXISTS idx_news_articles_source_published  ON public.news_articles USING btree (source_id, published_at DESC);
CREATE INDEX IF NOT EXISTS idx_news_articles_fetched_at        ON public.news_articles USING btree (fetched_at DESC);
