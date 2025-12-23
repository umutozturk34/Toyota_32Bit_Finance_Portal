# Project Roadmap

This roadmap defines the planned development phases and milestone-based releases of the Finance Portal project.  
Each version represents a stable and completed development milestone.

---

## 🔵 v0.1.0 – Infrastructure & Architecture (Month 1)

### Development Tasks
- [ ] Initialize Spring Boot project structure
- [ ] Configure layered architecture (controller, service, repository)
- [ ] Initialize React project structure
- [ ] Setup basic routing and layout
- [ ] Add PostgreSQL configuration
- [ ] Create Dockerfile for backend
- [ ] Create Dockerfile for frontend
- [ ] Add docker-compose configuration
- [ ] Configure application.yml profiles
- [ ] Add backend health check endpoint

### Version Release
- [ ] v0.1.0 – Infrastructure and base architecture completed

---

## 🔵 v0.2.0 – Authentication & Security (Month 2)

### Development Tasks
- [ ] Add Keycloak service to docker-compose
- [ ] Configure Keycloak realm and clients
- [ ] Integrate Spring Security with Keycloak
- [ ] Implement JWT-based authentication
- [ ] Add role-based authorization
- [ ] Create login and logout endpoints
- [ ] Implement frontend login page
- [ ] Add protected routes on frontend

### Version Release
- [ ] v0.2.0 – Authentication and authorization implemented

---

## 🔵 v0.3.0 – News & Market Data (Month 3)

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

## 🔵 v0.4.0 – Historical Data & Analysis (Month 4)

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

## 🟠 v0.5.0 – Portfolio, Kafka & Observability (Month 5)

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

## 🔵 v1.0.0 – Mobile & Finalization (Month 6)

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