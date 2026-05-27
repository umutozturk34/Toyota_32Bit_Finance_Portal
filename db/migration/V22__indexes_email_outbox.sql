
CREATE INDEX IF NOT EXISTS idx_email_outbox_pending      ON public.email_outbox USING btree (next_attempt_at) WHERE ((status)::text = 'PENDING'::text);
CREATE INDEX IF NOT EXISTS idx_email_outbox_in_flight    ON public.email_outbox USING btree (relayed_at)      WHERE ((status)::text = ANY ((ARRAY['RELAYED'::character varying, 'PROCESSING'::character varying])::text[]));
CREATE INDEX IF NOT EXISTS idx_email_outbox_failed       ON public.email_outbox USING btree (created_at)      WHERE ((status)::text = 'FAILED'::text);
CREATE INDEX IF NOT EXISTS idx_email_outbox_sent_purge   ON public.email_outbox USING btree (sent_at)         WHERE ((status)::text = 'SENT'::text);
CREATE INDEX IF NOT EXISTS idx_email_outbox_status       ON public.email_outbox USING btree (status);
CREATE INDEX IF NOT EXISTS idx_email_outbox_recipient    ON public.email_outbox USING btree (recipient_email);
CREATE INDEX IF NOT EXISTS idx_email_outbox_created      ON public.email_outbox USING btree (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_email_outbox_template     ON public.email_outbox USING btree (template_name);
CREATE INDEX IF NOT EXISTS idx_email_outbox_attempts     ON public.email_outbox USING btree (attempts) WHERE ((status)::text = ANY ((ARRAY['PENDING'::character varying, 'FAILED'::character varying])::text[]));
