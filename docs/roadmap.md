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
- [x] Add market data API clients (Twelve Data, AlphaVantage, CollectAPI)
- [x] Create exchange rate endpoints (TCMB)
- [x] Implement cryptocurrency service (CoinGecko)
- [x] Add precious metals service (CoinGecko tokenized)
- [x] Create US stocks service with multi-source support
- [x] Create BIST stocks service with fallback chain
- [x] Add BIST-FUND (GYO) support
- [x] Implement Redis caching (24h TTL)
- [x] Add dark mode toggle
- [x] Create market data UI (Stocks, Crypto, Metals pages)

### Version Release
- [x] v0.3.0 – News and market data modules completed ✅

---

## � v0.4.0 – Historical Data & Analysis (Partial Implementation)

### Development Tasks
- [x] Add historical data API client (CoinGecko only)
- [x] Implement date-range query support (Crypto only)
- [x] Create historical data REST endpoints (Crypto only)
- [x] Add ApexCharts candlestick visualization (Crypto only)
- [x] Implement interactive chart with zoom/pan functionality
- [x] Add time range controls (1M/3M/1Y)
- [x] Create ChartView page with crypto chart navigation
- [x] Add symbol-to-ID mapping for crypto navigation
- [ ] **PENDING**: Historical data for BIST stocks
- [ ] **PENDING**: Historical data for US stocks
- [ ] **PENDING**: Historical data for metals
- [ ] **PENDING**: Multi-instrument comparison
- [ ] **PENDING**: Moving average calculation
- [ ] **PENDING**: Technical analysis features

### Technical Implementation (Crypto Only)
- **CryptoHistoryService**: Fetches OHLCV candle data from CoinGecko API
- **Candle Entity/DTO**: OHLCV data structure for crypto historical data
- **Redis Caching**: 4,380 candles cached with self-healing mechanism
- **REST Endpoints**: 
  - `GET /api/v1/crypto/{coinId}/history` - Returns OHLCV candles
- **Supported Cryptos**: 12 major cryptocurrencies (BTC, ETH, USDT, BNB, SOL, XRP, USDC, ADA, AVAX, DOGE, TRX, DOT)
- **Frontend Components**:
  - **HistoricalChart.jsx**: ApexCharts candlestick chart with interactive features
  - **ChartView.jsx**: Chart page with time range buttons and crypto analysis
  - **Crypto.jsx**: Crypto list page with chart button navigation
  - **Route**: `/chart/:coinId` - Chart visualization for specific cryptocurrency
  - **Features**: Interactive candlestick charts, zoom/pan functionality, time range selection, chart button navigation from crypto cards

### Known Limitations
- Only cryptocurrency historical data is implemented
- BIST, US stocks, and metals historical data not yet available
- Technical analysis and comparison features are not implemented
- Chart functionality limited to crypto assets only

### Version Status
- [ ] v0.4.0 – **PARTIAL** - Historical data for cryptocurrency completed ⚠️

---

## � v0.5.0 – Complete Historical Data & Technical Analysis

### Development Tasks (Historical Data Expansion)
- [ ] Add BIST stocks historical data API integration
- [ ] Add US stocks historical data API integration  
- [ ] Add metals historical data API integration
- [ ] Implement unified historical data REST endpoints
- [ ] Add technical analysis service (SMA, EMA, RSI, MACD)
- [ ] Implement multi-asset comparison charts
- [ ] Add trend analysis and pattern recognition
- [ ] Create portfolio performance tracking
- [ ] Add watchlist functionality

### Technical Implementation Goals
- **Unified HistoricalDataService**: Support for all asset types (BIST, US, Crypto, Metals)
- **TechnicalAnalysisService**: Calculate SMA20/50, EMA, RSI, MACD indicators
- **ComparisonService**: Multi-asset chart overlays and correlation analysis
- **Portfolio Domain**: User portfolio tracking with P&L calculations
- **REST Endpoints**: 
  - `GET /api/v1/history/{type}/{symbol}?range=1M` - All asset types
  - `GET /api/v1/analysis/technical/{symbol}` - Technical indicators
  - `GET /api/v1/portfolio/{userId}` - Portfolio management
- **Frontend Features**:
  - Multi-asset chart comparison
  - Technical indicator overlays
  - Portfolio dashboard
  - Advanced time range selections
  - Correlation matrix views

### Version Release
- [ ] v0.5.0 – Complete historical data and technical analysis

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