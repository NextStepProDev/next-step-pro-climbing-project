# Next Step Pro Climbing

A booking and management system for climbing courses, trainings, and workshops.

Users can browse a public calendar, reserve time slots and sign up for events. Administrators get a full management panel for slots, events, reservations, and users.

## Key Features

### Booking & Calendar
- **Interactive calendar** with month, week, and day views — privacy-aware (other users' reservations shown only as "reserved")
- **Concurrent-safe reservations** — pessimistic locking with timeouts prevents double-booking under load
- **Dual-track waitlist** — automatic promotion with 24-hour confirmation windows for both time slots and events

### Content Management (CMS)
- **Block-based editor** for news articles and courses — TEXT, IMAGE, and VIDEO_EMBED blocks with drag & drop reordering
- **Draft / publish workflow** with scheduled publication dates
- **Multilingual content** — translation groups across PL / EN / ES with one-click duplication as a new translation
- **Image focal points** — stored X/Y coordinates for intelligent responsive cropping on thumbnails and photos

### Authentication & Security
- **Email / password + Google OAuth2** with automatic account provisioning and linking
- **Per-endpoint rate limiting** — separate Caffeine-backed IP buckets for auth, reservations, and admin routes
- **Account lockout** after repeated failed login attempts (brute-force protection)
- **Zero-copy file streaming** — `InputStreamResource` serving (0 MB RAM), UUID filenames, strict regex to block path traversal

### Newsletter & Communication
- **GDPR-compliant newsletter** — opt-in consent logging, tokenized one-click unsubscribe (no login required), full audit trail
- **Templated multi-language emails** — verification, password reset, waitlist notifications, and admin mass-mail
- **Activity audit log** — 23 action types tracking reservations, admin operations, and user lifecycle events

### Performance
- **Multi-tier Caffeine cache** — 2 min TTL for real-time calendar data up to 60 min for content details, with targeted eviction on mutations
- **Optimized queries** — N+1 fixes via SQL projections, batch loading of reservation counts and user state

## Tech Stack

### Backend
- **Java 25** + **Spring Boot 4.0.2**
- Spring Security + JWT + OAuth2 (Google)
- Spring Data JPA + **PostgreSQL 17**
- **Flyway** (53 migrations)
- SpringDoc OpenAPI (Swagger UI)
- Caffeine Cache (multi-tier TTL)
- Spring Boot Starter Mail
- JSpecify 1.0.0 (null-safety)
- Testcontainers + JUnit 5

### Frontend
- **React 19.2** + **TypeScript 5.9**
- **Vite 7.2**
- **Tailwind CSS 4**
- TanStack React Query 5
- React Router 7
- react-i18next (internationalization)
- date-fns, lucide-react, clsx

### Infrastructure
- Docker + Docker Compose
- GitHub Actions (CI/CD)
- GitHub Container Registry (GHCR)
- Cloud VM (production)
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

## Internationalization (i18n)

The application supports **3 languages**: Polish, English, and Spanish.

- **Detection:** browser language is detected automatically via `i18next-browser-languagedetector` (fallback: English)
- **Frontend:** react-i18next with 9 namespaces and bundled JSON locale files (`src/locales/{pl,en,es}/`)
- **Backend:** Spring `MessageSource` with `AcceptHeaderLocaleResolver` — API errors and validation messages are returned in the language from the `Accept-Language` header
- **Registration:** the detected browser language is sent to the backend and stored as the user's initial preference
- **Emails:** sent in the user's preferred language (stored in DB as `preferred_language`)
- **Sync:** language preference is saved to the database and restored on login across devices — manual changes in settings take priority over browser detection

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

## Backups

Automated daily backups at 3:00 AM via cron on the production server:

- **Database:** `pg_dump` exports the full PostgreSQL database, compressed with gzip
- **Files:** `tar` archives the `uploads/` directory (instructor photos, gallery, courses, news, assets)
- **Offsite sync:** `rclone` uploads backup files to Google Drive
- **Retention:** local backups older than 7 days are automatically deleted

### Manual restore

```bash
# Database
gunzip -c backup_YYYY-MM-DD.sql.gz | docker exec -i nsp-postgres-prod psql -U $POSTGRES_USER -d $POSTGRES_DB

# Files
tar -xzf uploads_YYYY-MM-DD.tar.gz -C /path/to/uploads/
```

## License

This project is licensed under the [MIT License](LICENSE).
