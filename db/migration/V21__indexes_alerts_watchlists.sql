
CREATE INDEX IF NOT EXISTS idx_price_alerts_user_created   ON public.price_alerts USING btree (user_sub, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_price_alerts_tracked_asset  ON public.price_alerts USING btree (tracked_asset_id);
CREATE INDEX IF NOT EXISTS idx_price_alerts_active_tracked ON public.price_alerts USING btree (tracked_asset_id) WHERE (active = true);
CREATE INDEX IF NOT EXISTS idx_price_alerts_user_active    ON public.price_alerts USING btree (user_sub) WHERE (active = true);
CREATE INDEX IF NOT EXISTS idx_price_alerts_direction      ON public.price_alerts USING btree (direction);
CREATE INDEX IF NOT EXISTS idx_price_alerts_triggered_at   ON public.price_alerts USING btree (triggered_at DESC) WHERE (triggered_at IS NOT NULL);
CREATE INDEX IF NOT EXISTS idx_price_alerts_user_tracked   ON public.price_alerts USING btree (user_sub, tracked_asset_id);

CREATE INDEX IF NOT EXISTS idx_watchlists_user_default_created ON public.watchlists USING btree (user_sub, is_default DESC, created_at);
CREATE INDEX IF NOT EXISTS idx_watchlists_user_sub             ON public.watchlists USING btree (user_sub);
CREATE INDEX IF NOT EXISTS idx_watchlists_is_default           ON public.watchlists USING btree (user_sub) WHERE (is_default = true);
CREATE INDEX IF NOT EXISTS idx_watchlists_updated_at           ON public.watchlists USING btree (updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_watchlist_items_tracked_asset    ON public.watchlist_items USING btree (tracked_asset_id);
CREATE INDEX IF NOT EXISTS idx_watchlist_items_user_created     ON public.watchlist_items USING btree (user_sub, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_watchlist_items_watchlist_order  ON public.watchlist_items USING btree (watchlist_id, display_order);
CREATE INDEX IF NOT EXISTS idx_watchlist_items_watchlist        ON public.watchlist_items USING btree (watchlist_id);
CREATE INDEX IF NOT EXISTS idx_watchlist_items_user_watchlist   ON public.watchlist_items USING btree (user_sub, watchlist_id);
