# Finance Portal — Roadmap

**Current shipped state:** v0.22 (Profile management + auth flow polish)
**Active line of work:** React Native mobile app (branch `feat/mobile`)

---

## Current Capabilities

### Identity & Authorization

- Keycloak OAuth2 / OpenID Connect; JWT-secured APIs; USER / ADMIN roles
- LDAP integration for enterprise deployments
- Two-tier rate limiting: Nginx edge zones (per-IP burst protection) + in-app Bucket4j over Redis (per-user, per-tier)
- 2FA setup, password change via Keycloak `executeActionsEmail`, email change with code flow
- Profile management (username + first/last name editable via Keycloak admin client, realm flag bootstrap)
- 2FA credential device list with per-device removal

### Market Data

| Module | Source | Coverage |
|--------|--------|----------|
| Crypto | CoinGecko + Binance | 40+ coins, 1y history, USD → TRY conversion |
| Equities | Yahoo Finance | BIST 100, BIST 30, MAIN_INDEX |
| FX | TCMB EVDS | Single source (commit `78f5d236`); döviz + efektif kurları, 22 currencies, 1995 backfill, incremental daily refresh |
| Funds | TEFAS | YAT (mutual funds) + BYF (ETFs), 5y history, returns/risk/allocation enrichment |
| Commodities | Yahoo Finance | Precious metals + futures + gold-gram synthetic derivative |
| Bonds & Bills | TCMB EVDS | Coupon-rate history, yield calc, maturity calendar; batch EVDS persist with price history |
| VIOP (Derivatives) | BIST | Futures + options as first-class assets, persisted snapshots, per-date FX, contract display names |
| Bank Rates | Doviz.com scrape | 28 currencies, display-currency aware |
| Macro | TCMB EVDS | 23 indicators (policy/TLREF/CPI/PPI + savings deposits by maturity); 14k+ historical points back to 1995 |
| News | RSS feeds | Multi-source; score-based auto-categorization; diacritic-insensitive tokenized search |

### Portfolio Management

- Lot-based positions — multiple entries per asset, different dates and prices
- Backdated entry for backtesting (e.g., BTC lot from 2015)
- Live valuation on every market refresh
- Intraday-granular snapshots (one row per refresh, not per day)
- VIOP partial close support with proper peer detection (temporal window, not current-open)
- Multi-currency P&L — `MultiCurrencyPnlCalculator` exposes per-currency pct in summary
- Real return overlay using TÜFE CPI growth
- Performance / Allocation / Realized PnL charts — currency-aware, snap markers to chart line, colored by event type
- Position detail page with multi-lot tracking
- Sell / reopen / partial close / rename / delete lifecycle
- Multi-portfolio CRUD (max 5 per user, index-backed check)
- Asset aggregate endpoint (closed lots restored to allocation)
- PDF report export — Puppeteer + Thymeleaf pipeline, dark/light × tr/en, three charts captured, rate-limited 2/hour
- Animated onboarding (idle → confirm → processing → success)
- Holiday/no-data resilience

### Charts & Technical Analysis

- LightweightCharts integration, range picker (1M / 3M / 6M / 1Y / 5Y / Max)
- Indicators: SMA, EMA, RSI, MACD; sub-charts scale proportionally in fullscreen
- Manual drawings: trend lines, Fibonacci, horizontal levels, freehand, annotations (Postgres JSONB)
- Per-asset preferences persisted (chart type, volume, magnet, fund toggles, indicator list)
- F5 restores zoom + pan + visible time range
- Compare mode — multi-asset, currency-aware, percent change from first common date, fixed baseline + secondary line toggle, inclusive backfill anchor matching beater semantic
- Fullscreen sub-chart proportional sizing via ResizeObserver

### Analytics

- `/analytics` page with three tools: Compare, Scenario, Inflation Beater
- Multi-asset compare (max 6), currency-aware, dataZoom, CPI baseline backfill
- Scenario simulator — hypothetical position with FX conversion at each day
- Inflation Beater — type/verdict ranking, period filters (1Y/3Y/5Y), real return vs nominal
- Beater window snaps endDate to last published CPI date (no day-to-day drift)
- DB-persisted beater snapshots via `BeaterSnapshot` repository
- Macro indicator panel with deposit matrix, indicator history modal, search filter, i18n descriptions

### Notifications (Microservice)

- Separate Spring Boot app on port 8082; shared `finance_db` with own Flyway history
- Kafka event-driven; 9 notification types fully wired (publisher → consumer → handler → UI):
  - PRICE_ALERT_FIRED, WATCHLIST_DELTA, SYSTEM, MARKET_OPENED, MARKET_CLOSED, MARKET_DATA_UPDATED, NEWS_PUBLISHED, PORTFOLIO_UPDATED, MACRO_INDICATORS_UPDATED
- Preference matrix: every type has email + in-app channel toggles + per-market chip selector
- Channels: in-app (bell, unread badge, paged list), SSE live stream, email (Thymeleaf, durable outbox + Kafka relay + circuit breaker)
- Kafka listener concurrency default 3; mail.dispatch + macro + email-change topics declared with 3 partitions
- Slot resolver — config-driven (`slot.yaml`) keyword → slot mapping shared across types
- Active-conversation skip via Caffeine + heartbeat (recipient viewing thread = no notif/email/bell)
- Admin broadcast — paginated dispatch via `NotificationFanoutService`

### Admin Panel

- Tracked-asset registry per market type (enable/disable per row)
- User management — Keycloak admin REST proxy with list/search/pagination, ban/unban (DB-backed `UserStatus`), password reset, 2FA reset
- Manual data-refresh triggers per market
- Task history panel
- News source CRUD

### Observability

- OpenTelemetry distributed tracing
- Kafka log pipeline; HTTP requests from both backends → OpenSearch via shared `RequestLoggingFilter`
- Resilience4j circuit breaker + retry + rate limiter
- Mail outbox pattern with exponential backoff + optimistic locking + DLQ
- PDF render audit log (portfolioId / theme / locale / bytes / duration)

### Settings

- Theme (light / dark), boot-time `localStorage` read prevents first-paint flash
- Language (TR + EN) — react-i18next, propagates to Keycloak login theme + backend messages
- Timezone, default chart range, report frequency
- Locale-aware Intl.NumberFormat + Intl.DateTimeFormat

### Quality Pipeline

- Backend module coverage all >80% (finance-user 88.5, finance-market 81.9, finance-portfolio 80.4, finance-news 84.6, finance-common 82.1, finance-app 97.0, finance-monolith-shared 90.8, notification-backend 88.6)
- GitHub Actions CI: finance-common test, backend reactor test, notification-backend test, frontend lint, SonarQube analysis, Docker image build per service, compose validate
- SonarCloud quality gate on PRs; self-hosted SonarQube Community profile-gated in compose
- ~2000+ AAA tests across all modules; @ParameterizedTest + @CsvSource for parametric cases

---

## Shipped Releases

### v0.1 — Foundation
Spring Boot scaffold, React + Vite, Docker Compose, PostgreSQL.

### v0.2 — Identity
Keycloak, JWT, LDAP integration.

### v0.3 — Admin Panel
Tracked-asset list, user management.

### v0.4 — Crypto
CoinGecko + Binance, 1y history, USD → TRY.

### v0.5 — Equities
Yahoo Finance BIST.

### v0.6 — FX
TCMB + Yahoo cross-pairs.

### v0.7 — Funds
TEFAS YAT + BYF.

### v0.8 — Observability
OpenTelemetry, Kafka log pipeline, OpenSearch dashboards.

### v0.9 — Bonds & Bills
EVDS integration, coupon history, simple yield.

### v0.10 — News
RSS sources, score-based categorization.

### v0.11 — Portfolio
Lot-based positions, intraday snapshots, performance chart.

### v0.12 — Unified Market
Single `/api/v1/market` endpoint, frontend overhaul, search suggestions.

### v0.13 — Commodities
Yahoo Finance commodity integration, gold-gram derivative, segment classification.

### v0.14 — User Module & Admin
- `user_preferences` Postgres (theme/language/timezone/default chart range/report frequency)
- `user_layouts` JSONB (V64) with react-grid-layout 12-col grid + dedup sanitizer
- Asset-card sparkline with echarts lazy + deterministic seed
- Keycloak admin proxy (list/search/ban/unban + 2FA + password change AIA)
- Onboarding gate, Keycloak email event listener, RateLimitTier OCP refactor

### v0.15 — Notification Microservice
- Standalone Spring Boot app on port 8082, Kafka event-driven
- Domain entities (Notification, NotificationPreference, PriceAlert, Watchlist + delta evaluator, UserPreferenceCache)
- Spring Mail + Thymeleaf templates
- In-app + SSE + email channels
- Market session minute-tick scheduler for open/closed transitions

### v0.16 — Chart Preferences & Drawings
- Postgres JSONB (V75) for `user_chart_preferences` + `user_chart_drawings` (MongoDB plan dropped)
- Per-asset config persisted; F5 restores zoom + pan + visible range
- Compare mode common-date baseline
- Backend consolidation — `UserChartDataController` + `UserChartDataFacade` returning bundle response
- ChartDefaultsProperties from `chart.yaml`

### v0.17 — Notification Expansion
- `news.published` + `portfolio.updated` Kafka topics with listener/handler/payload
- Config-driven `SlotResolver` (sabah/öğlen/akşam)
- 5 event publisher ports collapsed into polymorphic `EventPublisherPort` + `KafkaEventAdapter`
- `finance-monolith-shared` Maven module extracted from `finance-common`
- Nginx edge rate-limit zones + axios standard `Retry-After` fallback

### v0.18 — Internationalization
- react-i18next, TR + EN bundles
- Backend `Translator` + `ResourceBundleMessageSource` in finance-common
- Keycloak realm `internationalizationEnabled` + login theme messages
- Locale persisted as user preference → mirrored to Keycloak `locale` attribute → next JWT carries it
- Locale-aware Intl formatters

### v0.19 — Macro Indicators & Analytics
- **Macro module** — 23 EVDS series (policy/TLREF/CPI/PPI + savings deposits by maturity × TRY/USD/EUR), backfill to 1995, daily 17:30 cron, 14k+ points
- **MACRO_INDICATORS_UPDATED notification type** — change-aware fanout (FLAT + no-previous filtered), category accent colors (INFLATION amber / RATES indigo / DEPOSIT TRY emerald / USD cyan / EUR violet), email template dark/light + TR/EN
- **Analytics page** — `/analytics` route with Compare (multi-asset, currency-aware, max 6), Scenario simulator (FX at each day), Inflation Beater (type/verdict ranking)
- **Currency-aware throughout** — `AnalyticsPriceSeriesProvider` port + `PricedSeries` DTO, `NativeCurrencyStrategy` per asset type, historical FX adapter with daily cache
- **Macro panel UI** — deposit matrix, indicator history modal, search filter, descriptions
- **Currency math P0/P1 audit fixes** — crossViaTry EUR fallback, USDT tether guard, macro currency strategy, beater comparisonCurrency, bond coupon yield 100x, fxAt warn

### v0.20 — VIOP & Bank Rates
- **VIOP** — futures + options as first-class assets, persisted snapshots, per-date FX, contract display names, margin/strike/max loss surfaced on positions, edit entry + reopen closed positions, status filter
- **Bank Rates** — Doviz.com scrape piggybacking forex refresh, 28 currencies, display-currency aware
- **Portfolio integration** — VIOP peer detection uses temporal window, multi-lot same symbol with staggered close dates no longer drops chart to zero, viop close dialog aligned with sell dialog

### v0.21 — PDF Reports & Multi-Currency Polish
- **Portfolio PDF export** — Puppeteer + Thymeleaf pipeline, dark/light × tr/en, three charts captured (Allocation, Realized PnL, Performance), rate-limited 2/hour via PDF_EXPORT Bucket4j tier
- **Multi-currency P&L** — `MultiCurrencyPnlCalculator`, `CurrencyFramePct` DTO, per-currency pct in summary
- **Currency-aware UI** — Allocation center label TRY base, realized PnL shadeIndex+colorFor use realizedFor, summary frame amount routing
- **Beater extraction** — `BeaterCacheManager` extracted from `InflationBeaterService`
- **Snapshot service split** — `SnapshotCalculationService` refactored
- **Bond EVDS batch persist** with price history
- **Bond detail page** + inline macro modal
- **PDF error logging** to OpenSearch (com.finance logger → Kafka, no longer console-only)

### v0.22 — Profile & Frontend Polish
- **Profile management** — editable identity (username/firstName/lastName) via Keycloak admin client, 2FA device list with removal, `RealmConfigBootstrap` ensures `editUsernameAllowed=true`
- **Auth splash removal** — Login/Register pages mount → direct Keycloak redirect (no intermediate "Click to login" screen)
- **Analytics window stabilization** — Beater `resolveStableEndDate` snaps to last CPI publish; Compare `backFillToWindowStart` uses inclusive `<=` matching beater semantic
- **Notification parallelism** — Kafka listener concurrency default 1 → 3; mail.dispatch + macro.indicators.updated + user.email-change.code-requested declared with 3 partitions
- **Macro panel polish** — search filter, i18n descriptions
- **Mobile responsive sweep** — Bank rates sidebar stacks vertically, OHLC toolbar layout-shift fix, overview canvas cards mobile-friendly
- **Frontend unification** — primitives (Spinner, Button, IconButton, Card, InlineAlert, SideDrawer) replacing inline className/button across the codebase
- **Quality pipeline** — all backend modules >80% coverage, CI Sonar wired, controllers covered (UserProfileController, 2FA device endpoints, KeycloakAdminClient new methods)

---

## In Flight

### React Native mobile app (active — branch `feat/mobile`)

Assignment §3.3 requires a mobile app that mirrors web functionality (login, listing, detail) and talks to the backend via REST. Scope decisions pending:

- **Approach** — Capacitor (recommended: wraps existing React SPA, ~95% code reuse, ships to App Store + Play Store) vs full React Native rewrite vs PWA-only
- **Core screens** — login (Keycloak via in-app browser), portfolio list + detail, asset detail with chart, watchlist, alerts (admin pages + analytics page skipped on mobile)
- **Push notifications** — APNs (iOS) / FCM (Android) plugging into the existing notification microservice fanout
- **Biometric unlock** — Face ID / Touch ID for 2FA fallback
- **Offline portfolio snapshot** — TanStack Query cache hydration for last-known-good state when network drops

---

## Remaining

### Real mail service

Currently `notification-backend` ships emails via Mailpit (dev SMTP catcher on port 1025). For production:

- Pick a provider — SendGrid / AWS SES / Mailgun / Resend
- Update `SPRING_MAIL_HOST` + auth credentials in env / VDS config
- DKIM + SPF + DMARC records on the sending domain
- Bounce / complaint webhook handling (mark recipient as undeliverable in DB)
- Per-tenant from-address if multi-realm later

### VDS deployment

Move from local Docker Compose to a production VDS (Hetzner / DigitalOcean / Contabo). Compose stays as the orchestrator — same setup, just on remote hardware. Sequence:

- Provision VDS (Ubuntu 24.04 LTS, 8 GB RAM minimum, 100 GB SSD)
- Install Docker Engine + Compose plugin
- `git clone` + `cp .env.example .env` (populate prod values) + `docker compose up -d`
- Caddy or Traefik in front for automatic Let's Encrypt + HTTPS
- DNS A record → VDS IP
- Postgres backup cron → S3-compatible storage (Backblaze B2 / R2)
- Monitoring dashboard exposed publicly with basic auth
- Migration plan from local Postgres dump to VDS Postgres (`pg_dump` → scp → `psql`)

### Interim fixes (non-blocking, queued)

- 90 missing frontend i18n keys (56 `analytics.*`, 11 `marketOverview.macro.*`, 5 `nav.*`, 5 `portfolio.derivatives.*`, etc.)
- 38 orphan frontend i18n keys to prune
- 2 backend missing keys (`error.derivative.notClosed`, `error.portfolio.backfill.upstreamUnavailable`)
- Hardcoded YahooRangePolicy gap thresholds → `app.yahoo.range-policy.thresholds`
- 5 single-impl interfaces in notification services (justify or fold)
- Rich domain refactor — top candidates: `PortfolioDailySnapshot.computeRealizedReturn()`, `BaseAsset.applyMarketSnapshot()`, `DerivativePosition.close()`, `NotificationPreference.applyUpdate()`, `PriceAlert.updateThresholds()`

### Deferred (out of active scope)

- **Kubernetes** — manifests drafted and removed (2026-05-26). Docker Compose satisfies the assignment requirement and a single-node VDS. Re-author only if the project later needs multi-node scaling on a real cluster.

---

## Architectural Principles

- **Modular monolith + notification microservice** — `finance-app` orchestrates; market types in `finance-market`; portfolio/user/news in own Maven modules; shared code split: `finance-common` (both backends) + `finance-monolith-shared` (monolith-only)
- **Strategy pattern** — `MarketAssetProvider`, `MarketHistoryProvider`, `AssetPricingStrategy`, `NativeCurrencyStrategy` keep market-type extension Open–Closed
- **Sealed DTOs** — `MarketAssetMetadata` sealed interface; no `Map<String, Object>` in response surface
- **Port & Adapter** — `AssetPricingPort`, `PortfolioSnapshotPort`, `EventPublisherPort`, `UserStatusPort`, `AnalyticsPriceSeriesProvider`, `NewsSourceFetcher`, `TaskRefreshRegistry`
- **Event-driven** — Kafka topics for cross-module communication; polymorphic dispatch via `DomainEvent.topic()` + `partitionKey()`
- **Currency-aware end-to-end** — `Currency` enum + `Money` VO + per-asset `NativeCurrencyStrategy` + historical FX adapter with daily cache
- **Optimistic locking** — `@Version` on portfolios
- **Defensive error handling** — Resilience4j circuit breaker + retry; graceful skip when market closed; mail outbox with exponential backoff
- **API contract** — every response wrapped in `ApiResponse<T>`; entities never leak through controllers

---

## Tech Stack

### Backend
- Java 21, Spring Boot 4.0.6
- PostgreSQL 15 (Flyway migrations, separate history table per backend)
- Redis (cache + Bucket4j rate-limit buckets, cross-backend `rate-limit:{userSub}:{tierName}` keys)
- Apache Kafka (event bus + log pipeline)
- Keycloak (identity)
- Resilience4j (circuit breaker + retry + rate limiter)
- Caffeine (in-process cache)
- Thymeleaf (email + PDF templates)
- Flying Saucer + OpenPDF (PDF generation)
- Maven multi-module

### Frontend
- React 19, Vite, TailwindCSS 4
- Zustand (client state), TanStack Query (server state)
- LightweightCharts (price/portfolio), ECharts (allocation/sparkline)
- Framer Motion (animations)
- react-i18next, react-grid-layout, react-icons

### DevOps
- Docker Compose orchestration (production target is also Compose on a single VDS; K8s deferred until multi-node scaling is required)
- OpenTelemetry + OpenSearch dashboards
- Nginx reverse proxy (frontend container's own nginx routes /api/* + /api/v1/notifications/*; Caddy/Traefik planned in front for VDS TLS)
- GitHub Actions CI + SonarCloud (transitioning to self-hosted SonarQube)
- Mailpit (dev), real provider TBD for prod

---

## Release History

| Version | Module | Notes |
|---------|--------|-------|
| v0.22 | Profile & Frontend Polish | Profile management, auth splash removal, analytics window fix, kafka concurrency, frontend primitives |
| v0.21 | PDF Reports & Multi-Currency Polish | Puppeteer PDF, MultiCurrencyPnl, currency-aware UI, beater extraction |
| v0.20 | VIOP & Bank Rates | Futures/options first-class, 28-currency bank rates |
| v0.19 | Macro Indicators & Analytics | 23 EVDS series, MACRO_INDICATORS_UPDATED, /analytics page |
| v0.18 | Internationalization | TR/EN bundles, MessageSource, Keycloak theme, JWT locale claim |
| v0.17 | Notification Expansion | News/portfolio dispatch, slot resolver, monolith-shared module, edge rate-limit |
| v0.16 | Chart Preferences & Drawings | Per-asset chart prefs/drawings, compare baseline, fullscreen |
| v0.15 | Notification Microservice | Port 8082, Kafka event-driven, in-app + email + SSE |
| v0.14 | User Module & Admin | Preferences, layouts, admin proxy, 2FA |
| v0.13 | Commodities | Yahoo commodities, derivative calc, segment classification |
| v0.12 | Unified Market | Single `/api/v1/market`, frontend overhaul |
| v0.11 | Portfolio | Position management, snapshots, performance chart |
| v0.10 | News | RSS sources, category classification |
| v0.9 | Bonds & Bills | EVDS, coupon history, yield calc |
| v0.8 | Observability | OpenTelemetry, Kafka, OpenSearch |
| v0.7 | Funds | TEFAS YAT + BYF |
| v0.6 | FX | TCMB + Yahoo cross-pairs |
| v0.5 | Equities | Yahoo Finance BIST |
| v0.4 | Crypto | CoinGecko + Binance |
| v0.3 | Admin Panel | Tracked-asset list, user management |
| v0.2 | Identity | Keycloak, JWT, LDAP |
| v0.1 | Foundation | Spring Boot, React, Docker, PostgreSQL |
