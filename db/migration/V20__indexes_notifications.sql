
CREATE INDEX IF NOT EXISTS idx_notifications_user_created      ON public.notifications USING btree (user_sub, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread       ON public.notifications USING btree (user_sub, read_at) WHERE (read_at IS NULL);
CREATE INDEX IF NOT EXISTS idx_notifications_expires           ON public.notifications USING btree (expires_at) WHERE (expires_at IS NOT NULL);
CREATE INDEX IF NOT EXISTS idx_notifications_type              ON public.notifications USING btree (type);
CREATE INDEX IF NOT EXISTS idx_notifications_user_type_created ON public.notifications USING btree (user_sub, type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_created           ON public.notifications USING btree (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_metadata_gin      ON public.notifications USING gin (metadata);

CREATE INDEX IF NOT EXISTS idx_pref_system           ON public.notification_preferences USING btree (user_sub) WHERE ((inapp_system = true) OR (email_enabled AND (email_system = true)));
CREATE INDEX IF NOT EXISTS idx_pref_market_opened    ON public.notification_preferences USING btree (user_sub) WHERE ((inapp_market_opened = true) OR (email_enabled AND (email_market_opened = true)));
CREATE INDEX IF NOT EXISTS idx_pref_market_closed    ON public.notification_preferences USING btree (user_sub) WHERE ((inapp_market_closed = true) OR (email_enabled AND (email_market_closed = true)));
CREATE INDEX IF NOT EXISTS idx_pref_market_data      ON public.notification_preferences USING btree (user_sub) WHERE ((inapp_market_data_updated = true) OR (email_enabled AND (email_market_data_updated = true)));
CREATE INDEX IF NOT EXISTS idx_pref_news             ON public.notification_preferences USING btree (user_sub) WHERE ((inapp_news_published = true) OR (email_enabled AND (email_news_published = true)));
CREATE INDEX IF NOT EXISTS idx_pref_portfolio        ON public.notification_preferences USING btree (user_sub) WHERE ((inapp_portfolio_updated = true) OR (email_enabled AND (email_portfolio_updated = true)));
CREATE INDEX IF NOT EXISTS idx_pref_macro_indicators ON public.notification_preferences USING btree (user_sub) WHERE ((inapp_macro_indicators = true) OR (email_enabled AND (email_macro_indicators = true)));
CREATE INDEX IF NOT EXISTS idx_pref_email_enabled    ON public.notification_preferences USING btree (user_sub) WHERE (email_enabled = true);
CREATE INDEX IF NOT EXISTS idx_pref_updated_at       ON public.notification_preferences USING btree (updated_at DESC);
