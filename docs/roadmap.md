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
- [ ] Implement 2FA/TOTP backend endpoints
- [ ] Implement 2FA/TOTP frontend UI

### Version Release
- [ ] v0.2.0 – Authentication and authorization completed

---

## 🔵 v0.3.0 – News & Market Data

### Development Tasks
- [ ] Add external news API client
- [ ] Implement news fetch service
- [ ] Create news REST endpoints
- [ ] Add news list view
- [ ] Add news detail page
- [ ] Add market data API client
- [ ] Create exchange rate endpoints
- [ ] Implement market data UI

### Version Release
- [ ] v0.3.0 – News and market data modules completed

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