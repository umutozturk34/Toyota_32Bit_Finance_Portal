# Project Roadmap

This roadmap defines the planned development phases and milestone-based releases of the Finance Portal project.  
Each version represents a stable and completed development milestone.

---

## � API Reference Table

| API | Purpose | Endpoint/Data | Redis Cache | TTL | Rate Limit |
|-----|---------|---------------|-------------|-----|------------|
| **Twelve Data** | US Stocks (Primary) | 20 US stocks (AAPL, MSFT, NVDA...) | `stocks` | 24h | 800/day, 8/min |
| **AlphaVantage** | US Stocks (Fallback) | US stock quotes | `stocks` | 24h | 25/day, 5/min |
| **CollectAPI** | BIST Stocks + Funds | 20 BIST stocks + 10 GYO funds | `collectapi` | 24h | 100/day |
| **İş Yatırım** | BIST Stocks (Fallback) | BIST stock prices | `bist-stocks` | 24h | - |
| **Yahoo Finance** | BIST Stocks (Fallback) | Manual fallback only | `bist-stocks` | 24h | - |
| **Google Sheets** | BIST Stocks (Fallback) | Custom GOOGLEFINANCE data | `bist-stocks` | 24h | - |
| **CoinGecko** | Cryptocurrency | Top 20 crypto prices | `crypto` | 24h | 30/min |
| **CoinGecko** | Precious Metals | PAXG, XAUT, KAG | `metals` | 24h | 30/min |
| **NewsAPI** | Financial News | Business news articles | `news` | 24h | 100/day |
| **TCMB** | Exchange Rates | USD, EUR, GBP etc. | `exchange-rates` | 24h | - |

### Scheduled Tasks
| Task | Frequency | Description |
|------|-----------|-------------|
| US Stocks Fetch | Every 12h | Twelve Data → AlphaVantage fallback |
| BIST Stocks Fetch | 09:00 Mon-Fri | CollectAPI → İş Yatırım fallback |
| Crypto Fetch | Every 12h | CoinGecko top 20 |
| Metals Fetch | Every 12h | CoinGecko tokenized metals |
| News Fetch | Every 12h | NewsAPI business news |
| Exchange Rates | 15:00 daily | TCMB official rates |

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

## 🔵 v0.4.0 – Historical Data & Analysis

### Development Tasks
- [ ] Add historical data API client
- [ ] Implement date-range query support
- [ ] Create historical data REST endpoints
- [ ] Add line chart visualization
- [ ] Implement multi-instrument comparison
- [ ] Add moving average calculation
- [ ] Add basic trend visualization

### Version Release
- [ ] v0.4.0 – Historical data visualization and analysis completed

---

## 🟠 v0.5.0 – Portfolio, Kafka & Observability

### Development Tasks
- [ ] Create portfolio domain model
- [ ] Implement portfolio CRUD operations
- [ ] Add profit and loss calculation logic
- [ ] Add portfolio distribution charts
- [ ] Add Log4j2 configuration
- [ ] Setup Kafka broker via Docker
- [ ] Configure Kafka topics for logs
- [ ] Add Kafka appender to Log4j2
- [ ] Create Kafka log consumer service
- [ ] Integrate OpenSearch for log storage
- [ ] Add OpenTelemetry dependencies
- [ ] Configure tracing and metrics
- [ ] Create observability dashboards

### Version Release
- [ ] v0.5.0 – Portfolio management and Kafka-based observability completed

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