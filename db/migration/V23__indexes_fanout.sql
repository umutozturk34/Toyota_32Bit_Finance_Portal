
CREATE INDEX IF NOT EXISTS idx_notifications_user_type_dedup
    ON public.notifications USING btree (user_sub, type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_user_created_unread
    ON public.notifications USING btree (user_sub, created_at DESC)
    WHERE (read_at IS NULL);

CREATE INDEX IF NOT EXISTS idx_price_alerts_active_user
    ON public.price_alerts USING btree (tracked_asset_id, user_sub)
    WHERE (active = true);

CREATE INDEX IF NOT EXISTS idx_watchlist_items_tracked_user
    ON public.watchlist_items USING btree (tracked_asset_id, user_sub);

CREATE INDEX IF NOT EXISTS idx_pref_any_email
    ON public.notification_preferences USING btree (user_sub)
    WHERE (email_enabled = true AND (
        email_price_alerts = true OR
        email_watchlist = true OR
        email_system = true OR
        email_market_opened = true OR
        email_market_closed = true OR
        email_market_data_updated = true OR
        email_news_published = true OR
        email_portfolio_updated = true OR
        email_macro_indicators = true
    ));

CREATE INDEX IF NOT EXISTS idx_email_outbox_recipient_template
    ON public.email_outbox USING btree (recipient_email, template_name, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_email_outbox_recipient_failed
    ON public.email_outbox USING btree (recipient_email, created_at DESC)
    WHERE ((status)::text = 'FAILED'::text);
