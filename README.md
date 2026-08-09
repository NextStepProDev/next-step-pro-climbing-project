# Next Step Pro Climbing

A booking and management system for climbing courses, trainings, and workshops.

Users can browse a public calendar, reserve time slots and sign up for events. Administrators get a full management panel for slots, events, reservations, users, and site content. Athletes training one-on-one get a personal training calendar co-managed with their coach.

## Key Features

### Booking & Calendar
- **Interactive calendar** with month, week, and day views — privacy-aware (other users' reservations shown only as "reserved")
- **Concurrent-safe reservations** — pessimistic locking with timeouts prevents double-booking under load
- **Dual-track waitlist** — automatic promotion with 24-hour confirmation windows for both time slots and events
- **Invitation-only seats** — the coach holds named seats for registered users; availability is computed *per viewer*, so an invitee sees a free seat where everyone else sees "by invitation", and an invitee may book past the cutoff that applies to everyone else. Invitation emails are always a deliberate click, never a side effect of saving
- **Time proposals** — a logged-in user proposes a slot, either inside an availability window or freely; the coach turns a proposal into a real slot or event, which is the only way a proposal becomes accepted
- **Two ways to close a date** — an *unavailability* opens with zero seats and never accepts bookings, while *blocking* cancels an existing date and emails everyone who held a seat. Clients see one word for both; the back office keeps them apart

### Athlete Zone (Personal Training Calendar)
- **Explicit GDPR consent gate** — weight, RPE, and training feedback are health data under GDPR Art. 9, so an athlete passes a one-time consent screen before the calendar exists for them. The gate is enforced by the backend, not the UI, and revoking athlete status revokes the consent with it
- **TrainingPeaks-style shared plan** — coach-designated athletes get a personal training calendar co-managed with the coach (both sides create, edit, and comment on trainings), in month, month-tile, and week views, with a dedicated dot grid on phones
- **Two kinds of entry** — a *training* is a session; a *task* is a commitment held all day (a calorie ceiling, water, sleep). Separate rows on purpose: you can nail the session and blow the diet on the same day, and one checkbox cannot say both. Tasks are checked off, never rated; trainings may be scheduled to the hour or left untimed for the whole day
- **Completion tracking** — athletes check off trainings with feedback and a mandatory RPE (1-10) effort rating; "missed" status is derived, never stored
- **Goals and a trophy chest** — training and weight goals across three horizons, at most one active per slot. A weight goal closes *itself* from the measured trend, and only a self-closed goal can be reopened by the coach — a manually awarded trophy can be removed from the chest, but never quietly un-awarded
- **Body weight tracking** — morning readings with an application-side trend (the database keeps raw measurements only), a rapid-loss flag visible to the coach alone, and backfill for a forgotten weigh-in. Only the athlete can write a reading; deleting one never takes back an earned goal
- **Training materials & template library** — up to three links or uploaded files per entry, with YouTube/Instagram embedded inline; files are reference-counted, so duplicating an entry or applying a template shares one file on disk. The coach's template library carries both kinds of entry and copies content at the point of use
- **Duplicate, drag & drop, and a calendar clipboard** — copy/cut/paste that survives navigation and can paste into *another athlete's* calendar. A copy travels; a cut does not, because moving a row would drag its comment thread and completion into someone else's plan
- **Training statistics** — live-derived stats under the calendar: monthly counts with trend, week streaks, a GitHub-style 12-month activity heatmap, type breakdown, attendance rate, average RPE with an intensity distribution, and milestone badges. Tasks are counted apart from every training number, and every task count ships its denominator. Always computed from current data, never cached
- **Unread counters both ways** — per-viewer read markers drive "new from coach" / "new from athlete" badges, including alerts for deleted future trainings

### Content Management (CMS)
- **Block-based editor** for news articles and courses — TEXT and IMAGE blocks, plus VIDEO_EMBED in news, reordered block by block
- **Six managed modules** — news, courses, instructors, gallery albums, videos, and site settings (hero, badges, location section, calendar promo)
- **Draft / publish workflow** with scheduled publication dates
- **Multilingual content** — translation groups across PL / EN / ES with one-click duplication as a new translation (which starts as a draft)
- **Reference-counted media** — an image leaves the disk only once no language version still points at it
- **Image focal points** — stored X/Y coordinates for intelligent responsive cropping on thumbnails and photos
- **Unsaved-work protection** — full-page editors confirm before discarding an in-progress article, course, or video

### Authentication & Security
- **Email / password + Google OAuth2** — an existing account is linked only when the provider asserts a verified email
- **Strong-password policy** at every entry point (registration, reset, change) — length rules, rejection of passwords containing the user's own personal data, and a Have I Been Pwned breach check that fails open, so an unreachable third party can never block a signup. The frontend strength meter is a hint; the backend decides
- **Per-endpoint rate limiting** — separate Caffeine-backed IP buckets for auth, reservations, user, admin, training-calendar, and upload routes
- **Account lockout** after repeated failed login attempts (brute-force protection)
- **Zero-copy file streaming** — `InputStreamResource` serving (0 MB RAM), UUID filenames, strict regex to block path traversal

### Newsletter & Communication
- **GDPR-compliant newsletter** — opt-in consent logging, tokenized one-click unsubscribe (no login required), full audit trail
- **Templated multi-language emails** — verification, password reset, waitlist notifications, and admin mass-mail. Delivery retries and never throws: a dead SMTP server cannot roll back the booking that triggered the message
- **Isolated mail campaigns** — mass mail runs as a single sequential job on its own executor, so a 100-recipient campaign cannot starve transactional mail
- **A single "already happened" predicate** — no email is ever sent about a date that is over, and every mail decision in the admin panel asks the same question in the same timezone
- **Activity audit log** — tracks reservations, admin operations, athlete-plan changes, and user lifecycle events
- **Admin notification badges** — per-admin "seen" markers drive the pending-proposal and new-reservation counters; seats the admin booked themselves never light the dot

### Performance
- **Multi-tier Caffeine cache** — 2 min TTL for real-time calendar data up to 60 min for content details, with targeted eviction on mutations
- **Optimized queries** — N+1 fixes via SQL projections, batch loading of reservation counts and user state

### SEO
- **Pre-rendered public routes** at build time, so a crawler gets HTML instead of an empty SPA shell
- **Open Graph stubs** served to social scrapers only — search engines are deliberately excluded from that rule, because a JS-rendering crawler would follow the stub's refresh straight back into it
- **Sitemap endpoint** plus a post-deploy SEO smoke test that fails the deploy if crawler routing or `robots.txt` regresses

## Testing & Quality Gates

- **Backend** — unit tests plus integration tests on Testcontainers (real PostgreSQL), covering booking concurrency, waitlist promotion, mail decisions, and the athlete calendar's authorization
- **Frontend** — Vitest unit tests (densest around the training calendar) with TypeScript and ESLint required to be clean
- **End-to-end** — Playwright golden path in CI; it drives the frontend alone, so it needs neither a database nor an API
- **Architecture gates** — tests that read the source tree and fail the build on whole classes of mistake rather than single bugs: a bare `now()` outside the project timezone, an admin endpoint without `@PreAuthorize`, `@Transactional` or `@CacheEvict` on a non-public method (which silently does nothing while looking like it works), an activity type the admin panel cannot render, a translation key present in one language and missing in another, a raw `fetch` bypassing the API client, or a cache invalidation aimed at a key nobody queries.

  These exist because an audit is sampling, not a scan — each of these had already survived at least one review before its gate was written.

## Tech Stack

### Backend
- **Java 25** + **Spring Boot 4.1.0**
- Spring Security + JWT + OAuth2 (Google)
- Spring Data JPA + **PostgreSQL 17**
- **Flyway** (versioned migrations)
- SpringDoc OpenAPI (Swagger UI)
- Caffeine Cache (multi-tier TTL)
- Spring Boot Starter Mail
- JSpecify 1.0.0 (null-safety)
- Testcontainers + JUnit 5

### Frontend
- **React 19.2** + **TypeScript 6.0**
- **Vite 8**
- **Tailwind CSS 4**
- TanStack React Query 5
- React Router 7
- react-i18next (internationalization)
- date-fns, lucide-react, clsx
- Vitest + Testing Library, Playwright (E2E)

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
├── next-step-pro-climbing-hub/        # Docker Compose (dev/prod), .env, provisioning, load tests
├── .github/workflows/                 # CI/CD pipelines
├── docs/                              # Working notes (git workflow)
├── AUDIT.md                           # Audit coverage ledger — where the next review starts
└── VERSION                            # Application version
```

## Prerequisites

- Java 25 (JDK)
- Node.js 24+
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
- **Frontend:** react-i18next with per-feature namespaces and bundled JSON locale files (`src/locales/{pl,en,es}/`)
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
3. Production deploy — manual trigger in GitHub Actions; after the container is healthy, an external smoke test hits the public endpoints and fails the deploy if any isn't `200`

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
