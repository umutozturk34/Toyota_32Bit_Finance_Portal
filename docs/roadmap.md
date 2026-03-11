# 🚀 Finance Portal – Project Roadmap
**Modular & Chronological Development Stages**

This roadmap defines the planned development phases and milestone-based releases of the Finance Portal project. Each version represents a stable and completed development milestone.

---

## 🔵 v0.1.0 – Infrastructure
### Development Tasks
- Initialize backend (Spring Boot) and frontend (React) projects.
- Configure PostgreSQL, Docker, and application profiles.
- Setup basic routing, layouts, and health checks.

### Version Release
- ✅ **v0.1.0 – Infrastructure completed**

---

## 🔵 v0.2.0 – Authentication & Keycloak
### Development Tasks
- Integrate Keycloak for authentication and Role-Based Access Control (RBAC).
- Implement JWT-based security and token validation.
- Add login/register flows with frontend integration.

### Version Release
- ✅ **v0.2.0 – Authentication & security completed**

---

## 🔵 v0.3.0 – Admin Module
### Development Tasks
- Implement admin controllers for asset and user management.
- Add admin dashboard UI with data tables.
- Setup role-based permissions for admin actions.

### Version Release
- ✅ **v0.3.0 – Admin module completed**

---

## 🔵 v0.4.0 – Crypto Module
### Development Tasks
- Add cryptocurrency market data service integration.
- Create REST endpoints and dedicated frontend views.
- Implement caching layer with Redis for high-frequency data.

### Version Release
- ✅ **v0.4.0 – Crypto module completed**

---

## 🔵 v0.5.0 – Stock Module
### Development Tasks
- Add BIST (Borsa Istanbul) stock market data service.
- Create REST endpoints and interactive frontend charts.
- Implement advanced caching and historical charting.

### Version Release
- ✅ **v0.5.0 – Stock module completed**

---

## 🔵 v0.6.0 – Forex Module
### Development Tasks
- Add Forex data service (TCMB + Yahoo Finance).
- Create REST endpoints and currency conversion views.
- Implement multi-layered caching for exchange rates.

### Version Release
- ✅ **v0.6.0 – Forex module completed**

---

## 🔵 v0.7.0 – Fund Module
### Development Tasks
- Add Investment Fund (TEFAS) data service.
- Create REST endpoints and fund performance views.
- Implement unit tests and integration tests for data accuracy.

### Version Release
- ✅ **v0.7.0 – Fund module completed**

---

## 🔵 v0.8.0 – Full Observability Stack (OTel & Kafka)
### Development Tasks
- **OpenTelemetry Integration:** Deployed OTel Collector with OTLP receivers and multi-signal pipelines (Traces, Metrics, Logs).
- **Log4j Correlator:** Configured Log4j to be captured by OTel Agent, enabling Log-Trace correlation.
- **Message Broker (Kafka):** Implemented Kafka as a resilient buffer between OTel and OpenSearch to prevent data loss during high traffic.
- **SpanMetrics Connector:** Enabled real-time metric derivation (Request Count, Error Rates, Latency) directly from raw Traces.
- **OpenSearch & Dashboards:** Configured OpenSearch as the primary sink and built 4 specialized Vega-Lite dashboards (Executive, APM, Reliability,Integration & DB Health).



### Version Release
- ✅ **v0.8.0 – Observability & Monitoring completed**
- *Ref: Commit "release: v0.8.0 - integrated full observability stack"*

---

## ⚪ v0.9.0 – Performance & Security Hardening (Planned)
### Development Tasks
- **SSL/TLS (HTTPS):** Implement end-to-end encryption for all internal and external services.
- **Query Optimization:** Analyze PostgreSQL execution plans and optimize slow SQL queries.
- **Security Audit:** Conduct a full security scan for Keycloak and API Gateway layers.

---

## 🏁 v1.0.0 – Production Ready Release (Planned)
### Development Tasks
- Final system stabilization and performance benchmarking.
- Finalize documentation (API Docs, Deployment Guide).
- Final project presentation and live demo.

---

## 🛠 Project Standards
1. **Semantic Versioning:** Follows `Major.Minor.Patch` logic.
2. **Infrastructure as Code:** All configurations are version-controlled via Docker and OTel YAMLs.
3. **Continuous Monitoring:** Real-time health and performance tracking are integrated into the core architecture.