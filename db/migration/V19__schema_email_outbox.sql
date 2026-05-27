
CREATE SEQUENCE IF NOT EXISTS public.email_outbox_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.email_outbox (
    id                bigint NOT NULL DEFAULT nextval('public.email_outbox_id_seq'::regclass),
    recipient_email   character varying(320) NOT NULL,
    subject           text NOT NULL,
    template_name     character varying(64) NOT NULL,
    model             jsonb NOT NULL,
    theme             character varying(16) NOT NULL,
    locale            character varying(8)  NOT NULL,
    status            character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    attempts          integer DEFAULT 0 NOT NULL,
    last_attempt_at   timestamp without time zone,
    last_error        text,
    created_at        timestamp without time zone DEFAULT now() NOT NULL,
    sent_at           timestamp without time zone,
    next_attempt_at   timestamp without time zone,
    relayed_at        timestamp without time zone,
    version           bigint DEFAULT 0 NOT NULL,
    CONSTRAINT email_outbox_pkey PRIMARY KEY (id)
);

ALTER SEQUENCE public.email_outbox_id_seq OWNED BY public.email_outbox.id;
