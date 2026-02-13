# Project Roadmap

This roadmap defines the planned development phases and milestone-based releases of the Finance Portal project.  
Each version represents a stable and completed development milestone.

---

## 🔵 v0.1.0 – Infrastructure & Architecture
### Development Tasks
- [x] Initialize Spring Boot project structure
- [x] Configure layered architecture (controller, service, repository)
- [x] Initialize React project structure
- [x] Setup basic routing and layout
- [x] Add PostgreSQL configuration
- [x] Create Dockerfile for backend
- [x] Create Dockerfile for frontend
- [x] Add docker-compose configuration (PostgreSQL + Backend + Frontend)
- [x] Configure application.yml profiles
- [x] Add backend health check endpoint

### Version Release
- [x] v0.1.0 – Infrastructure and base architecture completed ✅

---

## 🔵 v0.2.0 – Authentication & Security

### Development Tasks
- [x] Add Keycloak service to docker-compose
- [x] Configure Keycloak realm and clients
- [x] Integrate Spring Security with Keycloak
- [x] Implement JWT-based authentication
- [x] Add role-based authorization (@PreAuthorize)
- [x] Create Keycloak setup automation script
- [x] Add OpenLDAP integration to Keycloak
- [x] Implement frontend Keycloak.js integration
- [x] Create AuthContext with React Context API
- [x] Add protected routes with role checking
- [x] Implement Login page with Keycloak redirect
- [x] Implement Register page (requires manual Keycloak activation)
- [x] Add JWT interceptor to Axios
- [x] Configure CORS for multiple origins
- [x] Add silent SSO check support
- [x] Implement 2FA/TOTP backend endpoints
- [x] Implement 2FA/TOTP frontend UI

### Version Release
- [x] v0.2.0 – Authentication and authorization completed ✅

---

## 🔵 v0.3.0 – News & Market Data

### Development Tasks
- [x] Add external news API client (NewsAPI)
- [x] Implement news fetch service with scheduling
- [x] Create news REST endpoints (public access)
- [x] Add news list view with category filtering
- [x] Implement frontend keyword-based categorization
- [x] Add market data API clients (Yahoo Finance, CoinGecko, TCMB)
- [x] Implement cryptocurrency service (CoinGecko)
- [x] Add precious metals service (CoinGecko tokenized)
- [x] Create BIST stocks service (Yahoo Finance)
- [x] Create forex service (TCMB + Yahoo Finance)
- [x] Add BIST-FUND (GYO) support
- [x] Implement Redis caching (24h TTL)
- [x] Add dark mode toggle
- [x] Create market data UI (Stocks, Crypto, Metals pages)

### Version Release
- [x] v0.3.0 – News and market data modules completed ✅

---

## � v0.4.0 – Historical Data & Candlestick Charts

### Development Tasks
- [x] Add historical data API clients (CoinGecko, Yahoo Finance, TCMB)
- [x] Implement OHLCV candle storage with PostgreSQL + Flyway
- [x] Create REST endpoints for crypto, stocks, and forex history
- [x] Add ApexCharts candlestick visualization component
- [x] Implement interactive chart features (zoom, pan, time ranges)
- [x] Create ChartView page with universal asset support
- [x] Historical data for Cryptocurrency (12 major coins)
- [x] Historical data for BIST stocks (XU30 constituents)
- [x] Historical data for Forex pairs (major currency pairs)
- [x] Add 365-day data retention with automated cleanup
- [ ] Historical data for US stocks
- [ ] Historical data for VİOP instruments
- [ ] Historical data for Funds (GYO, ETF)
- [ ] Historical data for Precious Metals

### Technical Implementation

**Backend Services:**
- **CryptoHistoryService**: 365-day OHLCV data from CoinGecko API (12 major cryptocurrencies)
- **StockHistoryService**: 5 year OHLCV data from Yahoo Finance (BIST XU30 stocks)
- **ForexHistoryService**: 5 year OHLCV data from Yahoo and TCMB (major currency pairs)
- **Database**: PostgreSQL with Flyway migrations for candle storage
- **Caching Strategy**: Redis with 24-hour TTL and self-healing mechanism
- **Data Retention**: Automated cleanup keeps exactly 365 days of historical data
- **Scheduler**: Daily Istanbul time sync for all data sources

**REST API Endpoints:**
- `GET /api/v1/crypto/{coinId}/history` - Cryptocurrency OHLCV candles
- `GET /api/v1/stocks/{symbol}/history` - BIST stock OHLCV candles
- `GET /api/v1/forex/{pair}/history` - Forex pair OHLCV candles

**Frontend Components:**
- **HistoricalChart.jsx**: ApexCharts candlestick with zoom, pan, and time range controls
- **ChartView.jsx**: Universal chart page supporting crypto, stocks, and forex
- **Navigation**: Chart button integration in Crypto, Stocks, and Forex list pages
- **Routes**: `/chart/:assetType/:assetId` - Dynamic chart routing for all asset types

**Supported Assets:**
- **Cryptocurrency**: 12 major coins (BTC, ETH, USDT, BNB, SOL, XRP, USDC, ADA, AVAX, DOGE, TRX, DOT)
- **BIST Stocks**: XU30 index constituents
- **Forex**: Major currency pairs (USD, EUR, GBP, JPY, TRY crosses)

### Scope & Limitations
- **Completed**: Crypto, BIST Stocks, and Forex historical data with candlestick charts
- **Pending**: US stocks, VİOP instruments, Funds (GYO/ETF), and Precious Metals historical data
- **Future Features**: Technical indicators, multi-asset comparison, portfolio tracking

### Version Release
- [ ] v0.4.0 – Historical data for all asset types (Crypto [✅], BIST [✅], Forex [✅], US Stocks [⏳], VİOP [⏳], Funds [⏳], Metals [⏳]) ⚠️

---

## ⚪ v0.5.0 – Technical Analysis & Advanced Features

### Development Tasks
- [ ] Implement technical analysis service (SMA, EMA, RSI, MACD, Bollinger Bands)
- [ ] Add technical indicators overlay on charts
- [ ] Implement multi-asset comparison charts
- [ ] Add trend analysis and pattern recognition
- [ ] Create portfolio performance tracking
- [ ] Implement watchlist functionality with alerts
- [ ] Add export functionality (CSV, Excel)

### Version Release
- [ ] v0.5.0 – Technical analysis tools and advanced trading features

---

## 🟠 v0.6.0 – Observability & Monitoring

### Development Tasks
- [ ] Add Log4j2 configuration with structured logging
- [ ] Setup Kafka broker via Docker Compose
- [ ] Configure Kafka topics for application logs
- [ ] Add Kafka appender to Log4j2
- [ ] Create Kafka log consumer service
- [ ] Integrate OpenSearch for log storage and indexing
- [ ] Add OpenTelemetry dependencies for tracing
- [ ] Configure metrics collection (Micrometer + Prometheus)
- [ ] Create observability dashboards (Grafana)
- [ ] Add application health monitoring
- [ ] Implement distributed tracing across microservices

### Version Release
- [ ] v0.6.0 – Monitoring and observability infrastructure completed

---

## 🔵 v1.0.0 – Mobile & Finalization

### Development Tasks
- [ ] Initialize React Native project
- [ ] Implement mobile authentication flow
- [ ] Add mobile news list view
- [ ] Add mobile market data view
- [ ] Fix cross-platform API issues
- [ ] Stabilize backend services
- [ ] Update roadmap documentation
- [ ] Prepare API endpoint documentation
- [ ] Update README with final project details

### Version Release
- [ ] v1.0.0 – Final release with mobile application and monitoring