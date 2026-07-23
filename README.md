# Enterprise Employee Management System (EEMS)

Monorepo containing both halves of the application:

- **`eems-backend/`** — Spring Boot 3.3 (Java 17) REST API: Auth/JWT, RBAC,
  Employee records, Department structure, Leave workflow, audit logging.
- **`eems-frontend/`** — Angular 18 SPA: login, role-scoped dashboard,
  employee list, department management (HR/Admin only), leave
  submission/approval.

Each has its own README with details specific to that half
(`eems-backend/README.md`, `eems-frontend/README.md`). This file covers
the project as a whole: layout, and how to run both together.

**New here?** [`ARCHITECTURE.md`](./ARCHITECTURE.md) is a single
document explaining every folder/file's purpose, the full database
schema with relationships, exactly how the Docker containers fit
together, and how the frontend and backend actually communicate
(REST/JSON, JWT auth flow, CORS vs. Docker's Nginx proxy). Start there
if you want the full picture before diving into either sub-project.

**Showing this off?** [`PORTFOLIO.md`](./PORTFOLIO.md) is a
screenshot-gallery-style overview meant for a GitHub profile/portfolio
README — add your own screenshots per `docs/screenshots/README.md` and
it's ready to link from your GitHub profile.

> **Build status:** the frontend was built and verified with
> `ng build --configuration production` in the environment that produced
> this repo (0 errors). The backend could **not** be compiled here — Maven
> Central wasn't reachable from that sandbox — so run `mvn clean verify`
> yourself as a first step; treat it as carefully written but unverified.

---

## Project Directory Structure

```
eems-project/
├── docker-compose.yml                      Postgres + backend + frontend stack
├── .env.example                            Copy to .env to override JWT_SECRET
├── eems-backend/                          Spring Boot API
│   ├── README.md
│   ├── pom.xml
│   ├── Dockerfile                          Multi-stage: Maven build → JRE runtime
│   └── src/
│       ├── main/
│       │   ├── java/com/eems/
│       │   │   ├── EemsApplication.java       Spring Boot entrypoint
│       │   │   ├── audit/
│       │   │   │   └── AuditService.java      Append-only audit log writer
│       │   │   ├── config/
│       │   │   │   ├── SecurityConfig.java    RBAC route rules, JWT filter chain, CORS
│       │   │   │   └── DevDataSeeder.java     Seeds sample users/data (dev profile only)
│       │   │   ├── security/
│       │   │   │   ├── JwtService.java            Token generation/validation
│       │   │   │   ├── JwtAuthFilter.java         Per-request bearer token check
│       │   │   │   └── CustomUserDetailsService.java
│       │   │   ├── sms/
│       │   │   │   ├── SmsSender.java             Interface - swap in a real provider here
│       │   │   │   └── ConsoleSmsSender.java      Dev stand-in: logs OTP instead of texting
│       │   │   ├── entity/                    JPA entities
│       │   │   │   ├── User.java, Role.java
│       │   │   │   ├── Employee.java, EmployeeStatus.java
│       │   │   │   ├── Department.java
│       │   │   │   ├── LeaveRequest.java, LeaveType.java, LeaveStatus.java
│       │   │   │   ├── PasswordChangeRequest.java     Pending SMS-confirmed password change
│       │   │   │   └── AuditLog.java
│       │   │   ├── repository/                Spring Data JPA repositories
│       │   │   ├── dto/                       Request/response DTOs (records)
│       │   │   ├── service/                   Business logic + RBAC visibility rules
│       │   │   │   ├── AuthService.java
│       │   │   │   ├── EmployeeService.java       manager/self scoping (FR-1.3)
│       │   │   │   ├── DepartmentService.java
│       │   │   │   ├── LeaveService.java          submit + approve/reject workflow
│       │   │   │   └── PasswordChangeService.java     verify → SMS OTP → confirm
│       │   │   ├── controller/                REST controllers (one per resource)
│       │   │   └── exception/                 Custom exceptions + global handler
│       │   └── resources/
│       │       └── application.yml            dev (H2) / prod (PostgreSQL) profiles
│       └── test/java/com/eems/
│           └── EemsApplicationTests.java       Context-load smoke test
│
└── eems-frontend/                          Angular SPA
    ├── README.md
    ├── angular.json
    ├── package.json
    ├── Dockerfile                           Multi-stage: Node build → Nginx runtime
    ├── nginx.conf                           Serves the SPA, proxies /api to backend
    └── src/
        ├── index.html, main.ts, styles.scss
        ├── environments/
        │   ├── environment.ts                 dev: points at localhost:8080/api
        │   └── environment.prod.ts
        └── app/
            ├── app.component.ts               Root shell (router-outlet host)
            ├── app.config.ts                  Providers: router, HttpClient + interceptor
            ├── app.routes.ts                  Routes + auth/role guards
            ├── core/                          Cross-cutting concerns
            │   ├── models/                    TS interfaces mirroring backend DTOs
            │   ├── services/                  AuthService + one API service per resource
            │   ├── guards/                    authGuard, roleGuard(...)
            │   └── interceptors/              auth.interceptor.ts (attaches JWT, handles 401)
            ├── features/                      One folder per screen
            │   ├── login/
            │   ├── dashboard/                 Role-scoped summary cards
            │   ├── employees/                 List (scoped by role)
            │   ├── departments/                HR/Admin only
            │   ├── leave/                     Submit + approve/reject
            │   └── change-password/           Current password → SMS OTP → confirm
            └── shared/layout/
                └── shell.component.ts          Logo, nav, account menu, footer - wraps all authenticated routes
```

---

## Running the full stack locally

**1. Backend** (from `eems-backend/`):

```bash
mvn spring-boot:run
```

Starts on `http://localhost:8080` in the `dev` profile (in-memory H2, no
setup needed) and seeds three accounts — see `eems-backend/README.md` for
the full list and credentials.

**2. Frontend** (from `eems-frontend/`, in a second terminal):

```bash
npm install
npm start
```

Starts on `http://localhost:4200` and talks to the backend at
`http://localhost:8080/api` (see `src/environments/environment.ts`).

**3. Log in** with one of the seeded accounts (e.g.
`manager@eems.local` / `ChangeMe123!`) and try switching between accounts
to see the RBAC scoping change what's visible on the dashboard, employee
list, and leave approval actions.

---

## Docker

> **Not verified in the environment that generated this** — Docker
> itself isn't installed there, and Docker Hub isn't reachable from it
> either, so none of this could actually be built or run before handing
> it to you. The Dockerfiles and compose config are written carefully
> (multi-stage builds, non-root backend user, correct Angular
> `dist/eems-frontend/browser` output path for the current Angular CLI)
> but treat them as unverified until you run `docker compose up --build`
> yourself.

A three-container stack: PostgreSQL, the Spring Boot backend (built with
a `maven:3.9-eclipse-temurin-17` → `eclipse-temurin:17-jre-alpine`
multi-stage `Dockerfile`), and the Angular frontend (built with
`node:20-alpine` and served by `nginx:1.27-alpine`, which also
reverse-proxies `/api/*` to the backend container — see
`eems-frontend/nginx.conf`).

```bash
docker compose up --build
```

- Frontend: `http://localhost:4300`
- Backend (direct, e.g. for the Power BI Web-connector endpoints): `http://localhost:8080/api`
- Postgres: `localhost:5433` (mapped from the container's internal 5432 — chosen to avoid clashing with a Postgres instance possibly already running natively on your machine) — `eems_user` / `eems_password` / db `eemsdb` (change these for anything beyond local testing)

First startup seeds the same three demo accounts as the `dev` profile
(`admin@eems.local`, `manager@eems.local`, `employee@eems.local`, all
`ChangeMe123!`) — there's no public registration endpoint, so without
this the compose stack would start with an empty database and no way to
log in at all.

**Set a real JWT secret** before anything beyond local testing:
```bash
cp .env.example .env
# edit .env and set JWT_SECRET to the output of: openssl rand -base64 48
```
`docker-compose.yml` reads `JWT_SECRET` from `.env` automatically.

**Tear down:**
```bash
docker compose down        # stop containers, keep the Postgres volume (data persists)
docker compose down -v     # also delete the Postgres volume (fresh start next time)
```

### The `docker` Spring profile — read this before using real data

The backend containers run under a **new `docker` profile**
(`application.yml`), not `prod`. It points at real PostgreSQL like `prod`
does, but uses `ddl-auto: update` instead of `prod`'s `validate` — so the
schema is created automatically and the stack works out of the box
without a separate migration step. That's a reasonable trade for a
demo/local stack; it is **not** a substitute for `prod` + a real
migration tool (Flyway/Liquibase — still not included, see each
sub-project's README) if you ever point this compose stack at a
database with data you actually care about. Set
`SPRING_PROFILES_ACTIVE=prod` on the backend container instead once
you've set up real migrations.

### Troubleshooting: "address already in use"

If `docker compose up` fails with something like `failed to bind host
port 0.0.0.0:5432/tcp: address already in use`, another process (often a
Postgres install already running natively on your machine, or — for
port 4200 — a local `ng serve`/`npm start` session) is using that port.
This compose file already maps Postgres to host port **5433** and the
frontend to host port **4300** for exactly this reason. If you still hit
a conflict on 5433, 4300, or 8080 (the backend), either stop whatever's
using that port, or change the host-side number (the left side of e.g.
`"4300:80"` in `docker-compose.yml`) to something free; the
container-internal port and inter-container connections (like the
backend's `DB_URL`) don't need to change either way, since containers
talk to each other over Docker's internal network regardless of host
port mappings.

### Rebuilding after code changes

`docker compose up --build` rebuilds images that changed. To force a
clean rebuild of one service specifically:
```bash
docker compose build --no-cache backend
docker compose up
```

---

## What's implemented vs. not yet

See the "What's implemented" section in each sub-project's README for the
full breakdown — including a later pass that added an audit log viewer,
delete for Departments/Positions, rate limiting on password-change
requests, leave balance bulk-allocate/carry-over, resend-code on
change-password, downloadable import templates, and employee table
pagination. In short: Auth/RBAC, Employee CRUD + onboarding/offboarding
with a full Create/Edit UI (not just import/read), Department structure
with CSV import/export, a Position entity (the first slice of a
normalized HR schema, replacing free-text job titles), Leave
submission/approval with a real balance calculator (allocated/used/
pending/available for ANNUAL/SICK/PARENTAL, enforced at submission not
just displayed), change-password with SMS-code confirmation, an HR
analytics dashboard with PDF/Excel export (now laid out as a dashboard
sidebar too), CSV/Excel employee bulk import, a Power BI data feed (SQL
reporting views for direct PostgreSQL connection, plus flat REST
endpoints for Power BI Desktop), and Docker containerization (see
"Docker" above) are done end-to-end across both layers. A fully
normalized enterprise schema (salary, attendance, documents, contracts,
performance reviews, training) is a natural next step but deliberately
not attempted in one pass — see `eems-backend/README.md` → "What's
implemented" for why. Three things are deliberately
stand-ins or unverified rather than the real/tested thing: SMS delivery
is console-logging only (no provider account available to wire up),
there's no real Power BI Embedded/Azure AD integration (needs tenant
credentials not available here), and the Docker setup couldn't actually
be built/run in the environment that generated it (no Docker, no
registry access) so treat it as unverified until you run it yourself —
each relevant README section explains exactly what to check or swap in.
Performance Review, the full GDPR module (DSAR
export, erasure workflow, consent tracking), Attendance clock-in, and
real-time dashboard updates are specified in the original SRS but not
yet built.
