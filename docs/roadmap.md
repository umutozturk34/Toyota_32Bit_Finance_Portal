# Finance Portal — Roadmap

**Target:** v0.20 (Futures & Options) — 25 May 2026

---

## Current Capabilities (v0.18.x)

### Identity & Authorization

- Keycloak-based OAuth2 / OpenID Connect authentication
- JWT-secured API access
- Role-based authorization (USER / ADMIN)
- LDAP integration for enterprise deployments
- Two-tier rate limiting: Nginx edge zones (per-IP burst protection) + in-app Bucket4j over Redis (per-user, per-tier)

### Market Data

| Module | Source | Coverage |
|--------|--------|----------|
| Crypto | CoinGecko + Binance | 40+ coins, 1y history, automatic USD → TRY conversion |
| Equities | Yahoo Finance | BIST 100, BIST 30, MAIN_INDEX |
| FX | TCMB + Yahoo Finance | Official rates + cross-pairs |
| Funds | TEFAS API | YAT (mutual funds) + BYF (ETFs), 5y history |
| Commodities | Yahoo Finance | Precious metals (gold/silver/platinum/palladium) + futures (oil, gas, copper, wheat, etc.); gold-gram derivative |
| Bonds & Bills | TCMB EVDS | Government bonds, treasury bills; coupon-rate history, simple-yield calculation, maturity calendar |
| News | RSS feeds | Multi-source; score-based auto-categorization (crypto / FX / equities / bonds / macro / general finance) |

### Portfolio Management

- **Lot-based position model** — multiple entries per asset (different dates and prices)
- **Backdated entry** — meaningful performance for a BTC lot opened in 2015
- **Live valuation** — portfolio value recomputed on every market refresh
- **Intraday-granular snapshots** — one row per market refresh, not per day
- **Performance chart** — total value, P&L, P&L percent; asset-type filter
- **Allocation chart** — pie by asset type or by asset code
- **Position detail page** — historical market value chart for a single asset, lot list
- **Holiday & no-data resilience** — TEFAS/Yahoo blanks gracefully skipped on public holidays; change percent computed against the last two candles
- **Animated onboarding** — first-portfolio creation wired to AnimatePresence with idle → confirm → processing → success phases

### Charts & Technical Analysis

- **Candle / line chart** — LightweightCharts integration, time range picker (1M / 3M / 6M / 1Y / 5Y / Max)
- **Indicators** — SMA, EMA, RSI, MACD; sub-chart panes scale proportionally to viewport in fullscreen mode
- **Manual drawings** — trend lines, Fibonacci, horizontal levels, freehand, annotations — persisted per asset (Postgres JSONB)
- **User preferences** — chart type, volume toggle, magnet mode, fund toggles persisted per asset; F5 restores zoom + pan + visible time range
- **Compare mode** — two assets compared as percent change from their first common date with a sane baseline; drawings made in compare mode are not persisted
- **Fullscreen mode** — sub-chart heights distributed proportionally to viewport

### Notifications

- **Notification microservice** — port 8082, separate Spring Boot app, Kafka event-driven dispatch
- **Notification types** — price alert, watchlist delta, system, market opened/closed, market data updated, **news published** (slot-labelled morning/midday/evening), **portfolio updated** (daily snapshot), **macro indicators updated** (inflation / rates / deposit deltas with category-colored cards)
- **Preference matrix** — every type has independent email + in-app channel toggles; per-market chip selector
- **Channels** — in-app (bell icon, unread badge, paged list) + SSE live stream + email (Thymeleaf templates, durable outbox + Kafka relay + circuit breaker)
- **Admin broadcast** — paginated dispatch through `NotificationFanoutService` so a single system payload reaches every user without N+1
- **Slot labelling** — config-driven (`slot.yaml`) keyword → slot mapping shared across notification types

### Admin Panel

- Tracked-asset registry per market type (enable/disable per row)
- User management — Keycloak admin REST proxy: list/search/pagination, ban/unban (DB-backed `UserStatus`), password reset email, 2FA reset
- Manual data-refresh triggers (snapshot / candle / full update per market)
- Task history panel (init, scheduler, manual triggers)

### Observability

- OpenTelemetry distributed tracing
- Kafka log pipeline; every HTTP request from both backends logged to OpenSearch via shared `RequestLoggingFilter`
- OpenSearch + dashboards
- Resilience4j circuit breaker, retry, rate limiter

### Settings

- Theme (light / dark) — read from `localStorage` before mount so first paint matches user choice (no light → dark flash)
- Language (Turkish + English) — react-i18next driven, locale persisted as a user preference, propagates to Keycloak login theme and backend message bundles
- Timezone, default chart range, report frequency preferences
- Onboarding-completed flag

---

## Shipped Releases

### v0.14 — User Module & Admin ✓

**Goal:** User preferences, layout customization, admin panel, identity-adjacent ops.

**Scope:**

- **Preferences** (`user_preferences` Postgres) — theme, language, timezone, default chart range, report frequency, onboarding flag; ThemeContext backed by TanStack Query; settings sidebar UI
- **Overview customization** (`user_layouts` Postgres JSONB, V64) — sections the user pulls onto the overview page (BIST indices, per-market top movers, watchlist, news) — visibility, order, position (`react-grid-layout` 12-col grid; drag-drop + resize), each card with its own config (news category chips + round-robin priority, watchlist picker, asset-card pin list); save-side `OverviewWidgetDedupSanitizer` (max 12 widgets, max 4 asset-card widgets); asset-card sparkline (echarts lazy + deterministic seed); single anchored settings popover lifted to `MarketDataPage`
- **Admin user management** — Keycloak admin REST proxy (`KeycloakAdminClient`) with list/search/pagination/ban/unban; self-ban guard
- **2FA setup** — Keycloak account API integration, settings sidebar inline panel
- **Password change** — Keycloak `executeActionsEmail` flow with mailed link; UPDATE_PASSWORD AIA flow; rate limit (`CredentialActionTier` 3/hour)
- **Onboarding gate** — first-login modal, `onboarding_completed` flag
- **Keycloak email event listener** — UPDATE_PASSWORD/REMOVE_TOTP/UPDATE_TOTP automatic notification mail
- **Rate-limit OCP refactor** — `RateLimitTier` interface + per-tier `@Component` (adding a tier = new file)

---

### v0.15 — Notification Microservice & Watchlists ✓

**Goal:** Event-driven notification system — price alerts, watchlists, in-app + email notifications hosted as a separate Spring Boot application.

**Architecture:** Standalone `finance-notification-app` Maven sibling, port 8082. Talks to the monolith via **Kafka events** (no REST callbacks). Shared `finance_db` but with its own Flyway history table.

**Scope (delivered):**

- **Microservice scaffolding** — new Spring Boot app, port 8082, Nginx regex routing
- **Kafka event contract**:
  - `market.updated` — published by the monolith after market refresh; consumed by `notification-service-market` (alert/watchlist evaluator) and `notification-data-updated` (data-updated dispatch)
  - `user.preferences.updated` — compacted topic, AFTER_COMMIT publish on user preference change
- **Domain entities** (notification side):
  - `Notification`, `NotificationPreference` (channel matrix × type + master email switch + per-market opt-in CSV)
  - `PriceAlert` (threshold, direction, one-shot/recurring) + `AlertEvaluator`
  - `Watchlist` (named, multiple per user, default "Favoriler" auto-created, max 20) + `WatchlistItem` + `WatchlistEvaluator` (delta detection)
  - `UserPreferenceCache` (local cache fed by Kafka consumer)
- **Email dispatch** — Spring Mail + Thymeleaf templates (`price-alert.html`, `watchlist-delta.html`, `market-opened.html`, `market-closed.html`, `market-data-updated.html`)
- **In-app notifications** — bell icon, unread badge, paged list
- **SSE live stream** — `/api/v1/notifications/stream` (real-time push)
- **Market session notifications** — minute-tick `MarketSessionScheduler` for open/closed transitions (24/7 sentinel bypass for crypto/news), Kafka `MarketDataUpdateListener` for data-updated (Caffeine eventId idempotency + `auto-offset-reset: latest` redelivery-safe), source-aware title (morning/midday/evening/daily), per-market opt-in chip selector, terminal-HUD `MarketStatusBadge` on every asset detail page
- **Frontend** — watchlist + price-alerts UI, settings notification matrix (master email + per-type email/inapp + per-market chip selector), `MarketStatusBadge` + `MarketSelectionChips` terminal-HUD components

---

### v0.16 — Chart Preferences & Drawings ✓

**Goal:** Per-asset persistence of chart configuration and manual drawings so a logged-in user gets the same chart back on any device.

**Storage decision:** Postgres JSONB instead of MongoDB. The MongoDB plan from earlier roadmap iterations was dropped — adding a second datastore for two opaque blobs did not justify the operational cost when JSONB columns deliver the same shape with the existing Flyway pipeline.

**Scope (delivered):**

- **Schema (V75)** — `user_chart_preferences (user_sub, tracked_asset_id, config JSONB)` + `user_chart_drawings (user_sub, tracked_asset_id, drawings JSONB)`, both with `(user_sub, tracked_asset_id)` UNIQUE and `ON DELETE CASCADE` to `tracked_assets` so deleting a tracked asset purges every user's drawings/preferences for that symbol
- **Per-asset preferences** — chart type (line/candle), volume toggle, magnet mode, fund toggles (investor count, portfolio size), Fibonacci tools, indicator list (SMA/EMA/RSI/MACD with period + colour); F5 restores all of them
- **Manual drawings** — trend lines, Fibonacci, horizontal levels, freehand, annotations persisted per asset; debounced PUT removed in favour of immediate save so a fast F5 cannot lose unsaved state
- **Visible time range persistence** — zoom + pan + visible range stored per asset and restored on F5 (replaces the earlier in-memory `viewportStorage.js`)
- **Compare mode baseline** — two-symbol percent comparison anchored at the first common date; visible-range subscribe rebases when the user zooms; drawings made while compare is active are deliberately not persisted
- **Fullscreen sub-chart sizing** — sub-charts (RSI / MACD / Volume / Investor count / Portfolio size) inherit `isFullscreen` and scale heights proportionally to the viewport via ResizeObserver
- **Backend consolidation** — `UserChartPreferenceController` + `UserChartDrawingController` merged behind one `UserChartDataController` + `UserChartDataFacade` returning a `UserChartBundleResponse`; `UserChartPreferenceMapper` + `UserChartDrawingMapper` (MapStruct) + `JsonNodeConverter` so services no longer hand-wire `JsonNode` ↔ `Map`
- **Backend defaults from yaml** — `ChartDefaultsProperties` reads `chart.yaml` so frontend gets backend-driven defaults instead of hardcoded fallbacks

---

### v0.17 — Notification Expansion ✓

**Goal:** Surface news refreshes and daily portfolio snapshots as their own notification streams, and tidy up the publisher / consumer infrastructure that grew during v0.15–v0.16.

**Scope (delivered):**

- **News dispatch** — `news.published` Kafka topic (separate from `market.updated` so news no longer surfaces as "market data updated"); `NewsScheduler` publishes after each refresh with article count; new `NewsPublishedListener` + `NewsPublishedHandler` + `NewsPublishedPayload` on the consumer side; idempotency cache zone
- **Portfolio dispatch** — `portfolio.updated` Kafka topic, emitted per saved snapshot by `PortfolioSnapshotService`; corresponding listener / handler / payload pair so users get a one-line "Portföyünüz güncellendi" with daily P&L when their nightly snapshot lands
- **Config-driven slot resolver** — `SlotProperties` (`notification.keywords`) loaded from `slot.yaml` and consumed by a shared `SlotResolver`; replaces the hard-coded `SLOT_KEYWORDS` table inside `MarketDataUpdatedHandler` and powers the new `NewsPublishedHandler` titles ("Sabah haberleri · 5 yeni başlık"); adding a new slot = config edit, no code change
- **Event publisher consolidation** — five separate ports (`MarketUpdateEventPort` + `NewsEventPort` + `PortfolioEventPort` + `UserPreferenceEventPort` + `EmailChangeEventPort`) and five Kafka adapters collapsed into a single `EventPublisherPort` + `KafkaEventAdapter`; events implement `DomainEvent` and supply their own `topic()` + `partitionKey()` so the adapter dispatches polymorphically (no `switch`)
- **`finance-monolith-shared` Maven module** — extracted from `finance-common` so notification-backend pulls only what it actually uses (cache, config, exception handler, filter tier, security port, model entities, event records, KafkaTopics, Bucket4j rate-limit infra). Monolith-only utilities (Batch helpers, sealed asset metadata DTOs, task tracking, pricing/snapshot ports, `CandlePeriod`, `MoneyTRY`, `Percentage`, `RedisKeys`) live in the new module; notification-backend has zero dependency on it
- **Edge rate-limit + axios polish** — Nginx `limit_req_zone` per route (auth 5 r/s burst 10, api 50 r/s burst 100, static 200 r/s burst 200) + `limit_conn` per IP; axios falls back to standard `Retry-After` header when Bucket4j's custom one is absent
- **Frontend surface** — 2 new preference toggle rows (news published, portfolio updated), 5 new `NotificationPanel` `TYPE_META` entries (market opened/closed/data-updated + news published + portfolio updated), theme boot splash reads `localStorage` before React mount so the first paint matches the chosen theme even if the preference query fails
- **Cleanup** — dead `MarketType.NEWS` + `SessionMarket.NEWS` + `MoneyTRYConverter` removed; news scheduler stopped publishing onto `market.updated`; `RequestLoggingFilter` reinstated under `com.finance.common.filter` so both backends log every request to OpenSearch

---

### v0.18 — Internationalization ✓

**Goal:** Turkish / English language toggle; open the platform up to international users.

**Scope (delivered):**

- `react-i18next` integration with TR + EN bundles built into the frontend
- Asset types, bond types, fund types, segment labels, slot labels and notification copy migrated into translation files
- Backend `Translator` + `ResourceBundleMessageSource` in `finance-common`; locale resolved from JWT `locale` claim → `Accept-Language` → default
- Notification handler titles, in-app bodies and Thymeleaf email templates pull from `messages_{tr,en}.properties`
- Keycloak realm `internationalizationEnabled` + `loginTheme=finance`; theme `messages_{tr,en}.properties` for login / register / OTP / password / verify-email flows
- Settings sidebar language toggle persists via user preference; mirrored to Keycloak `locale` user attribute so the next JWT carries it; landing pages get an inline TR/EN switch next to the theme toggle
- Locale-aware number / date formatting (`Intl.NumberFormat` + `Intl.DateTimeFormat` via `useLocale`)

---

## In Flight (post-v0.18)

### Macro indicators + notifications (2026-05-21)

- Daily TCMB EVDS pull for 23 macro series (policy / TLREF / CPI / PPI + TL/USD/EUR savings deposit by maturity); incremental refresh + backfill from 1995
- New notification type **`MACRO_INDICATORS_UPDATED`** — fired by the monolith only when at least one indicator gained a new point; consumer filters out unchanged (FLAT) and no-previous rows, sorts by absolute % delta, picks a hero card, computes up/down counters
- Category accent colors: INFLATION amber, RATES indigo, DEPOSIT TRY emerald / USD cyan / EUR violet
- Kafka topic `macro.indicators.updated`; idempotency cache `macroIndicatorsUpdatedProcessedEventIds`
- Preference matrix gained email + in-app toggles for the new type (V35 migration, `email_macro_indicators` default off, `inapp_macro_indicators` default on)
- Email template `macro-indicators-updated.html` — dark/light + TR/EN aware, hero block + summary chip bar + category-colored card list with ▲/▼ change pills
- Frontend: settings preference row, `NotificationPanel` TYPE_META entry, TR/EN i18n bundles
- AAA tests cover: changedCodes capture, publish-on-change vs skip-on-empty, FLAT/no-prev filter, UP/DOWN rendering, hero pick, category accent

### Backend service split (Faz 3a, 2026-05-21)

- `PortfolioPerformanceService` 498 → **263 LOC** orchestrator only
- Extracted helpers: `PerformanceEventAssembler` (145 LOC, trade window + event projection + spot/derivative proceeds) and `PerformanceAggregationHelper` (119 LOC, by-type / by-code aggregation with capped detail + Other bucket)
- 385 portfolio + 231 backend tests stay green

## Pending Releases

### v0.19 — Forex Refactor (EVDS migration)

**Goal:** Consolidate the FX data source — replace TCMB scraping + Yahoo Finance cross-pairs with the single TCMB EVDS API that already drives bonds and bills.

**Scope:**

- `ForexClient` rewritten against `evds2.tcmb.gov.tr/service/evds` (token already provisioned via `EVDS_API_KEY`)
- Single source for spot, history and cross-pairs — drop the Yahoo Finance dependency for FX
- Series-code mapping table (`TP.DK.USD.A`, `TP.DK.EUR.A`, …) loaded from a config block instead of switch statements
- Holiday + weekend gap handling reused from the bonds path
- Per-pair rate-limit budget honoured at the client; backoff on 429
- No frontend break — `forexService.getHistory(code, period)` contract preserved

### v0.20 — Futures & Options (final)

**Goal:** Leveraged position management on derivatives — the final release before the 25 May deadline.

**Scope:**

- Futures and options contract data model — separate entities so option-specific fields (strike, expiry, call/put) live on their own
- Long / short position open; for options, call / put + strike + expiry picker
- Margin calculation — initial + maintenance — and a margin-call alert dispatch via the existing notification path
- Daily settlement (mark-to-market) tied into `PortfolioSnapshotScheduler`
- Auto-close on expiry; for options the strike price + IV (implied volatility) appear on the asset detail page
- Leverage ratios displayed on the position card; open positions table with real-time P&L

---

## Architectural Principles

- **Modular monolith + notification microservice** — `finance-app` is the orchestration entry point; market types live under `finance-market`; portfolio / user / news in their own Maven modules; shared code split between `finance-common` (used by both backends) and `finance-monolith-shared` (monolith-only)
- **Strategy pattern** — `MarketAssetProvider`, `MarketHistoryProvider`, `AssetPricingStrategy` keep market-type extension Open–Closed
- **Sealed DTOs** — `MarketAssetMetadata` sealed interface for type safety instead of `Map<String, Object>`
- **Port & Adapter** — cross-module dependencies inverted via `AssetPricingPort`, `PortfolioSnapshotPort`, `EventPublisherPort`, `UserStatusPort`
- **Event-driven** — monolith → notification microservice over Kafka topics (`market.updated`, `news.published`, `portfolio.updated`, `user.preferences.updated`); polymorphic dispatch through `DomainEvent.topic()` + `partitionKey()`
- **Optimistic locking** — `@Version` on portfolios guards against race conditions
- **Defensive error handling** — Resilience4j circuit breaker + retry; graceful skip when a market is closed
- **API contract** — every response is wrapped in `ApiResponse<T>`; entity classes never leak through controllers

---

## Tech Stack

### Backend

- Java 21, Spring Boot 4
- PostgreSQL 15 (Flyway migrations)
- Redis (cache + Bucket4j rate-limit buckets)
- Apache Kafka (event bus + log pipeline)
- Keycloak (identity)
- Resilience4j
- Maven multi-module

### Frontend

- React 19, Vite, TailwindCSS 4
- Zustand (client state) + TanStack Query (server state)
- ECharts + LightweightCharts
- Framer Motion

### DevOps

- Docker Compose orchestration
- OpenTelemetry + OpenSearch dashboards
- Nginx reverse proxy (edge rate-limit + burst protection)
- GitHub Actions CI

---

## Release History

| Version | Module | Notes |
|---------|--------|-------|
| v0.18 | Internationalization | TR / EN bundles, MessageSource backend, Keycloak login theme, JWT locale claim, locale-aware formatters |
| v0.17 | Notification Expansion | News / portfolio dispatch, slot resolver, monolith-shared module, edge rate-limit |
| v0.16 | Chart Preferences & Drawings | Per-asset chart prefs / drawings, compare baseline, fullscreen |
| v0.15 | Notification Microservice | Separate port-8082 service, Kafka event-driven, in-app + email dispatch |
| v0.14 | User Module & Admin | Preferences, overview customization, admin proxy, 2FA |
| v0.13 | Commodities | Yahoo Finance commodity integration, derivative calculation, segment classification |
| v0.12 | Unified Market | Single `/api/v1/market` endpoint, frontend overhaul, search suggestions |
| v0.11 | Portfolio | Position management, snapshots, performance chart |
| v0.10 | News | RSS sources, category classification |
| v0.9 | Bonds & Bills | EVDS integration, coupon history, yield calculation |
| v0.8 | Observability | OpenTelemetry, Kafka, OpenSearch |
| v0.7 | Funds | TEFAS YAT + BYF |
| v0.6 | FX | TCMB + Yahoo cross-pairs |
| v0.5 | Equities | Yahoo Finance BIST |
| v0.4 | Crypto | CoinGecko + Binance |
| v0.3 | Admin Panel | Tracked-asset list, user management |
| v0.2 | Identity | Keycloak, JWT, LDAP |
| v0.1 | Foundation | Spring Boot, React, Docker, PostgreSQL |
