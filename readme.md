# Finance Portal

A full-stack finance portal project focusing on secure architecture, centralized authentication, and system observability.

## 🚀 Project Overview
This project demonstrates the development of a modern finance portal using industry-standard technologies and structured versioning.  
The development lifecycle is managed through a roadmap and milestone-based releases.

## 🛠️ Technology Stack
- Backend: Spring Boot
- Frontend: React
- Database: PostgreSQL
- Authentication: Keycloak
- Messaging & Logging: Apache Kafka
- Observability: OpenTelemetry
- Containerization: Docker & Docker Compose

## 📍 Project Status
**In development**

## 📁 Documentation
All project documentation is maintained under the `docs/` directory.

- 🗺️ Roadmap: **[docs/roadmap.md](./docs/roadmap.md)**

## 🚀 Quick Start

### Prerequisites
- Docker & Docker Compose
- Git

### Setup
1. Clone the repository:
```bash
git clone <your-repo-url>
cd finance_portal
```

2. Copy environment file and configure API keys:
```bash
cp .env.example .env
# Edit .env with your actual API keys
```

3. Start all services:
```bash
docker-compose up -d
```

4. Access the application:
- Frontend: http://localhost:3000
- Backend: http://localhost:8080
- Keycloak Admin: http://localhost:8080/admin

### API Keys Required
The application requires API keys from:
- **NewsAPI** (newsapi.org) - For financial news
- **Twelve Data** (twelvedata.com) - For US stock data  
- **AlphaVantage** (alphavantage.co) - Stock data fallback
- **CoinGecko** (coingecko.com) - Cryptocurrency data
- **CollectAPI** (collectapi.com) - Turkish market data

Add your keys to `.env` file based on `.env.example` template.

## 📦 Versioning
The project follows semantic versioning:
- `v0.x.x` – Development and feature milestones
- `v1.0.0` – First stable release

---
