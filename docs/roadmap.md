# Project Roadmap – Modular & Chronological

This roadmap defines the planned development phases and milestone-based releases of the Finance Portal project.  
Each version represents a stable and completed development milestone.  

---

## 🔵 v0.1.0 – Infrastructure
### Development Tasks
- Initialize backend (Spring Boot) and frontend (React) projects
- Configure PostgreSQL, Docker, and application profiles
- Setup basic routing, layouts, and health checks

### Version Release
- ✅ v0.1.0 – Infrastructure completed

---

## 🔵 v0.2.0 – Authentication & Keycloak
### Development Tasks
- Integrate Keycloak for authentication and role-based access
- Implement JWT-based security
- Add login/register flows with frontend integration

### Version Release
- ✅ v0.2.0 – Authentication & security completed

---

## 🔵 v0.3.0 – Admin Module
### Development Tasks
- Implement admin controllers for asset and user management
- Add admin dashboard UI
- Setup role-based permissions for admin

### Version Release
- ✅ v0.3.0 – Admin module completed

---

## 🔵 v0.4.0 – Crypto Module
### Development Tasks
- Add cryptocurrency market data service
- Create REST endpoints and frontend views
- Implement caching with Redis

### Version Release
- ✅ v0.4.0 – Crypto module completed

---

## 🔵 v0.5.0 – Stock Module
### Development Tasks
- Add BIST stock market data service
- Create REST endpoints and frontend views
- Implement caching and basic charting

### Version Release
- ✅ v0.5.0 – Stock module completed

---

## 🔵 v0.6.0 – Forex Module
### Development Tasks
- Add Forex data service (TCMB + Yahoo)
- Create REST endpoints and frontend views
- Implement caching

### Version Release
- ✅ v0.6.0 – Forex module completed

---

## 🔵 v0.7.0 – Fund Module
### Development Tasks
- Add Fund data service
- Create REST endpoints and frontend views
- Implement caching and tests

### Version Release
- ✅ v0.7.0 – Fund module completed

---

## 🛠 How to Continue / Next Steps
Since the roadmap path is flexible and new features/modules may be added at any time, you can follow this approach:

1. **Add new module/feature**
   - Copy the format of a previous module section
   - Assign the next version number (`v0.x.0`)
   - List the development tasks
   - Mark status as `⚠️ In progress` initially

2. **Complete module/feature**
   - Once development is done, change status to `✅ Completed`
   - Commit and tag the release if applicable

3. **Keep chronological order**
   - Always append new modules/features at the bottom
   - This ensures roadmap reflects actual development sequence

4. **Update roadmap regularly**
   - Every time a module is started or completed, update the roadmap
   - Use Git commits to track changes over time

5. **Optional future planning**
   - If you want, you can add “Planned” sections for upcoming features, but keep them separate from completed modules