# Next Step Pro Climbing

A booking and management system for climbing courses, trainings, and workshops.

Users can browse a public calendar, reserve time slots and sign up for events. Administrators get a full management panel for slots, events, reservations, and users.

## Tech Stack

### Backend
- **Java 25** + **Spring Boot 4.0.2**
- Spring Security + JWT (email/password auth)
- Spring Data JPA + **PostgreSQL 17**
- **Flyway** (database migrations)
- SpringDoc OpenAPI (Swagger UI)
- Caffeine Cache
- JSpecify 1.0.0 (null-safety)
- Testcontainers + JUnit 5

### Frontend
- **React 19.2** + **TypeScript 5.9**
- **Vite 7.2**
- **Tailwind CSS 4**
- TanStack React Query 5
- React Router 7
- date-fns, lucide-react, clsx

### Infrastructure
- Docker + Docker Compose
- GitHub Actions (CI/CD)
- GitHub Container Registry (GHCR)
- Oracle Cloud (production VM)
- Nginx Proxy Manager + Let's Encrypt SSL

## Project Structure

```
next-step-pro-climbing-project/
├── next-step-pro-climbing-backend/    # Spring Boot API
├── next-step-pro-climbing-frontend/   # React SPA
├── next-step-pro-climbing-hub/        # Docker Compose (dev/prod), .env
├── .github/workflows/                 # CI/CD pipelines
├── VERSION                            # Application version (1.0.0)
└── CLAUDE.md                          # AI context
```

## Prerequisites

- Java 25 (JDK)
- Node.js 22+
- Docker + Docker Compose
- PostgreSQL 17 (via Docker)

## Quick Start (Development)

### 1. Database + Mailhog

```bash
cd next-step-pro-climbing-hub
docker compose -f docker-compose.dev.yml up -d
```

This starts:
- **PostgreSQL** on port `5432`
- **Mailhog** — SMTP on `1025`, Web UI at `http://localhost:8025`

### 2. Backend

```bash
cd next-step-pro-climbing-backend
cp .env.example .env  # if exists, fill in the variables
./gradlew bootRun
```

Backend starts at `http://localhost:8080`.

### 3. Frontend

```bash
cd next-step-pro-climbing-frontend
npm install
npm run dev
```

Frontend starts at `http://localhost:5173`.

## API Documentation

Available after starting the backend:
- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON:** `http://localhost:8080/v3/api-docs`

## Deployment

CI/CD via GitHub Actions:
1. Push/PR to `main` runs tests (backend: Gradle test, frontend: lint + typecheck + build)
2. Merge to `main` builds Docker images and pushes to GHCR
3. Production deploy — manual trigger in GitHub Actions

Production: `nextsteppro.pl`

## License

This project is licensed under the [MIT License](LICENSE).
