# EEMS Architecture — Complete Explanation

This document explains, in one place: what every folder and file is for,
the full database schema and how the tables relate, how the three
Docker containers fit together, and exactly how the frontend and
backend talk to each other. It complements — doesn't replace —
`eems-backend/README.md` and `eems-frontend/README.md`, which go deeper
on individual features.

---

## 1. The big picture

```
┌─────────────────┐        HTTP/JSON over REST        ┌──────────────────┐
│  Angular SPA     │  ────────────────────────────────▶ │  Spring Boot API │
│  (eems-frontend) │  ◀──────────────────────────────── │  (eems-backend)  │
└─────────────────┘        JWT in Authorization header  └──────────────────┘
                                                                   │
                                                                   │ JDBC
                                                                   ▼
                                                          ┌──────────────────┐
                                                          │   PostgreSQL /   │
                                                          │   H2 (dev only)  │
                                                          └──────────────────┘
```

Three independently-deployable pieces: an Angular single-page app, a
Spring Boot REST API, and a relational database. Nothing shares memory
or a process — every interaction between them is either an HTTP call
(frontend → backend) or a JDBC connection (backend → database). That
separation is exactly what makes the Docker setup in section 4 possible
without any code changes: each piece just needs to know the *network
address* of the other, not anything about how it's built or run.

---

## 2. Backend — `eems-backend/`, folder by folder

```
eems-backend/
├── pom.xml                     Maven build file - all dependencies, Java version, plugins
├── Dockerfile                  Multi-stage: Maven build → JRE-only runtime image
├── .dockerignore                Keeps target/, .idea/ out of the Docker build context
├── db/
│   └── powerbi-reporting-views.sql   Read-only SQL views + a dedicated DB role, for
│                                      connecting Power BI Desktop directly to Postgres
└── src/
    ├── main/
    │   ├── resources/
    │   │   └── application.yml  FOUR Spring profiles in one file (--- separated):
    │   │                        default, dev (H2), prod (real Postgres, ddl-auto=validate),
    │   │                        docker (real Postgres, ddl-auto=update - see section 4)
    │   └── java/com/eems/
    │       ├── EemsApplication.java      Spring Boot entrypoint (the `main` method)
    │       │
    │       ├── entity/                   JPA @Entity classes = one class per database table
    │       │   (full list and relationships in section 3)
    │       │
    │       ├── repository/               Spring Data JPA interfaces - one per entity.
    │       │                             Each is just method signatures (e.g.
    │       │                             `findByEmployeeId(Long id)`); Spring generates
    │       │                             the SQL at startup from the method name.
    │       │
    │       ├── dto/                      Request/response shapes (Java records) that
    │       │                             actually cross the network - entities never do.
    │       │                             This is deliberate: it lets a response omit
    │       │                             sensitive fields (e.g. EmployeeResponse has no
    │       │                             nationalId) without touching the entity at all.
    │       │
    │       ├── service/                  Business logic. This is where "can this
    │       │                             MANAGER see this EMPLOYEE's salary?" gets
    │       │                             decided - not in the controller, not in
    │       │                             SecurityConfig (which only does coarse,
    │       │                             role-based URL gating).
    │       │
    │       ├── controller/               @RestController classes - map an HTTP verb +
    │       │                             path to a service method. Deliberately thin:
    │       │                             a controller method is almost always one line
    │       │                             that calls straight into a service.
    │       │
    │       ├── config/
    │       │   ├── SecurityConfig.java   THE file that decides, per URL + HTTP verb,
    │       │   │                        which roles can even reach a controller (see
    │       │   │                        section 5 for how this composes with service-
    │       │   │                        layer checks).
    │       │   └── DevDataSeeder.java    Runs on startup in "dev"/"docker" profiles only
    │       │                             - creates the 3 demo logins and starter data.
    │       │                             Never runs in "prod".
    │       │
    │       ├── security/                 JWT generation/validation (JwtService,
    │       │                             JwtAuthFilter), password-hashing bridge to
    │       │                             Spring Security (CustomUserDetailsService),
    │       │                             the temporary-password generator
    │       │                             (CredentialGenerator), and the in-memory
    │       │                             rate limiter (RateLimiter).
    │       │
    │       ├── sms/                      SmsSender interface + ConsoleSmsSender (logs
    │       │                             instead of texting - no real provider account
    │       │                             was available to wire up here).
    │       │
    │       ├── report/                   PDF/Excel/CSV file generation (Apache PDFBox,
    │       │                             Apache POI, Apache Commons CSV) - the actual
    │       │                             byte-producing code for exports.
    │       │
    │       ├── storage/                   FileStorageService - real local-disk storage for
    │       │                             employee document uploads (path-traversal
    │       │                             protected, one subfolder per employee). Not
    │       │                             S3/MinIO/Azure Blob - see the backend README.
    │       │
    │       ├── audit/                    AuditService - the one place that writes to
    │       │                             the append-only audit_log table. Every
    │       │                             service that changes something calls this.
    │       │
    │       └── exception/                Custom exceptions (ResourceNotFoundException,
    │                                     ForbiddenOperationException,
    │                                     TooManyRequestsException) + GlobalExceptionHandler,
    │                                     which turns them into consistent JSON error
    │                                     responses instead of raw stack traces.
    │
    └── test/java/com/eems/
        └── EemsApplicationTests.java     One smoke test: does the Spring context start.
```

### Why this many layers?

`Controller → Service → Repository → Entity` is the standard Spring
Boot layering, and the reason it's worth keeping straight:

- **Controller**: HTTP concerns only (status codes, path variables, request bodies).
- **Service**: business rules and access control ("only self or HR can see this").
- **Repository**: pure data access, no logic.
- **Entity**: the actual table shape.

A concrete example of why this matters: the "self, direct manager, or
HR/Admin/Auditor" visibility rule is duplicated (deliberately, matching
this codebase's existing style of small per-service duplication over a
premature shared abstraction) across `EmployeeService`,
`LeaveBalanceService`, `EmployeeAddressService`, `EmergencyContactService`,
`EmployeeDocumentService`, and `EmploymentContractService` — the same
rule applies everywhere an employee's own data is at stake. Keeping
that logic in services, not scattered across controllers, is what
makes it possible to state the rule once per service and trust it's
actually enforced consistently.

---

## 3. Database schema — every table and how they connect

Seventeen tables. Grouped by what they're about:

**Identity & access**

| Table | Purpose | Key relationships |
|---|---|---|
| `app_user` | Login identity: email, password hash, role, enabled flag, `mustChangePassword` flag | 1:1 with `employee` (optional — SUPER_ADMIN/HR_ADMIN/AUDITOR accounts often have no linked employee) |
| `password_change_request` | A pending SMS-confirmed password change (new hash + OTP hash, not yet applied) | belongs to one `app_user` |
| `audit_log` | Append-only: who did what to what, when | references entities by type+id as plain strings, not FKs — so a log entry survives even if the referenced row is later deleted |

**Organization structure**

| Table | Purpose | Key relationships |
|---|---|---|
| `department` | Org unit (name, location) | self-referencing `parent_department_id` for hierarchy |
| `position` | Job title as a managed entity (title, grade, salary band, job description) — replaces old free-text job titles | belongs to one `department` |
| `job_posting` | A recruitment opening (title, description, visibility, status, dates) — posting management only, no candidate/application/interview data model | optional links to `department` and `position` |

**Employee core + extension tables** (the "don't put everything in one table" schema)

| Table | Purpose | Key relationships |
|---|---|---|
| `employee` | Core identity: name, hire/exit date, status | belongs to `department`, `position`; self-referencing `manager_id`; 1:1 with `app_user` |
| `employee_address` | One address per employee | 1:1 with `employee` |
| `emergency_contact` | Zero or more per employee | many:1 to `employee` |
| `employee_document` | Document metadata, plus real uploaded bytes stored on local disk via `FileStorageService` for actual uploads (vs. an external `fileUrl` for metadata-only records) | many:1 to `employee` |
| `salary` | Effective-dated history — a new row per change, never overwritten | many:1 to `employee`; access restricted to self + HR_ADMIN/SUPER_ADMIN only, deliberately excluding managers |
| `employment_contract` | Contract type, dates, freeform terms | many:1 to `employee` |

**Leave**

| Table | Purpose | Key relationships |
|---|---|---|
| `leave_request` | One leave submission | many:1 to `employee`; optional many:1 `approved_by` (another employee) |
| `leave_balance` | Allocated/used days per employee, leave type, and calendar year — only for ANNUAL/SICK/PARENTAL | many:1 to `employee`; unique on (employee, leave_type, year) |

### How the pieces connect (text ER diagram)

```
department ──┬──< position
             └──< employee >── position
                     │  │
                     │  └──── manager_id (self-FK: employee's own manager)
                     │
       ┌─────────────┼───────────────┬───────────────┬──────────────────┬───────────────────┐
       ▼             ▼               ▼               ▼                  ▼                    ▼
employee_address  emergency_    employee_        salary          employment_        leave_request
   (1:1)          contact(1:N)  document(1:N)    (1:N, history)  contract(1:N)      (1:N)
                                                                                          │
                                                                                          ▼
                                                                                    leave_balance
                                                                                    (1 per type/year)

app_user (1:1 with employee, optional) ──< password_change_request (1:N)
```

Why `employee` is split into six tables instead of one wide table:
each extension has a different *shape* of relationship (1:1 for
address, 1:many for contacts/documents/contracts, effective-dated
history for salary) and — more importantly — a different *access
rule*. Salary is restricted to self + HR/Admin, explicitly excluding
a direct manager; everything else follows the standard self/manager/
HR/Admin/Auditor rule. Cramming all of that into one `employees` table
would mean either over-exposing salary data through the same endpoint
as job title, or building field-level access control inside a single
giant DTO — the normalized version makes the security boundary the same
thing as the table boundary, which is easier to get right and easier to
audit.

### What's deliberately *not* in this schema yet

Payroll processing (beyond the `salary` history table), attendance
clock-in/out, performance reviews, training/certifications, and
recruitment are all out of scope so far — each is a genuine subsystem
of its own. See `eems-backend/README.md` → "What's implemented" for the
full reasoning on each.

---

## 4. How it's containerized

Three services in `docker-compose.yml`, each with its own Dockerfile:

```
┌────────────────────────────────────────────────────────────┐
│  docker-compose.yml (all three share one Docker network)   │
│                                                              │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────────┐│
│  │  postgres     │   │  backend      │   │  frontend        ││
│  │  postgres:16  │◀──│  Spring Boot  │◀──│  Nginx serving   ││
│  │  -alpine      │   │  (own image,  │   │  the built       ││
│  │               │   │  built from   │   │  Angular app,    ││
│  │  Host port:   │   │  eems-backend/│   │  + reverse-      ││
│  │  5433 → 5432  │   │  Dockerfile)  │   │  proxying /api   ││
│  │               │   │               │   │  to "backend"    ││
│  │               │   │  Host port:   │   │                  ││
│  │               │   │  8080 → 8080  │   │  Host port:      ││
│  │               │   │               │   │  4200 → 80       ││
│  └──────────────┘   └──────────────┘   └──────────────────┘│
└────────────────────────────────────────────────────────────┘
```

**`eems-backend/Dockerfile`** — multi-stage:
1. `maven:3.9-eclipse-temurin-17` stage: compiles the jar (dependencies
   cached in their own layer, so a source-only change doesn't
   re-download the internet).
2. `eclipse-temurin:17-jre-alpine` stage: copies just the built jar in —
   no Maven, no source, no build tools in the final image. Runs as a
   non-root user.

**`eems-frontend/Dockerfile`** — multi-stage:
1. `node:20-alpine` stage: `npm ci` + `ng build --configuration production`.
2. `nginx:1.27-alpine` stage: copies the built static files
   (`dist/eems-frontend/browser/`) in, plus a custom `nginx.conf`.

**`eems-frontend/nginx.conf`** is the piece that makes the frontend
container able to talk to the backend container at all: it reverse-
proxies any request to `/api/*` over to `http://backend:8080/api/*` —
`backend` here isn't a real hostname, it's the **Docker Compose service
name**, which Docker's internal DNS resolves to that container's
internal IP automatically. Everything else (`/`, `/dashboard`, etc.)
falls back to `index.html` so Angular's client-side router can take
over, even on a hard refresh of a deep link.

**The `docker` Spring profile** (`application.yml`) is what the backend
container runs under — real PostgreSQL like `prod`, but
`ddl-auto: update` instead of `prod`'s `validate`, so the schema is
created automatically on first boot without a separate migration step.
`DevDataSeeder` also runs in this profile, so the compose stack is
usable immediately (there's no public registration endpoint — without
seeded accounts, a fresh stack would have no way to log in at all).

**Networking summary**: the *browser* only ever talks to
`localhost:4200` (the frontend container). It never talks to
`localhost:8080` directly in the Dockerized setup — Nginx is doing that
hop server-side. This is also why CORS doesn't come up in Docker at
all: from the browser's point of view, `/api/*` is same-origin.

---

## 5. How the frontend and backend actually communicate

### 5.1 The two different setups

| | Local dev (`npm start` + `mvn spring-boot:run`) | Docker Compose |
|---|---|---|
| Frontend origin | `http://localhost:4200` (Angular's own dev server) | `http://localhost:4200` (Nginx) |
| Backend origin | `http://localhost:8080` | not exposed to the browser at all |
| How `/api/*` calls reach the backend | Browser calls `localhost:8080/api/*` directly (`environment.ts` has the full URL) | Nginx reverse-proxies `/api/*` → `backend:8080/api/*` |
| Why CORS matters | Yes — different origins (4200 vs 8080), so `SecurityConfig`'s CORS config (`corsConfigurationSource()`) explicitly allow-lists `http://localhost:4200` | No — same-origin from the browser's perspective |

This is why there are two environment files:
`src/environments/environment.ts` (dev: `apiUrl: 'http://localhost:8080/api'`)
and `environment.prod.ts` (prod/Docker build: `apiUrl: '/api'`, a
relative path that Nginx's proxy rule intercepts).

### 5.2 The request/response shape

Every API call is plain JSON over HTTP. There's no GraphQL, no
WebSockets (yet — see the frontend README's "not yet implemented"
list), no server-sent events. A typical request from Angular:

```
Angular component
  → calls an *ApiService (e.g. EmployeeApiService.list())
    → HttpClient.get<Employee[]>('http://localhost:8080/api/employees')
      → auth.interceptor.ts attaches: Authorization: Bearer <accessToken>
        → Spring's JwtAuthFilter reads that header, validates the token,
          populates Spring Security's Authentication object
          → SecurityConfig's URL rules check the caller's role
            → the controller method runs, with Authentication injected
              → the service does its own finer-grained check if needed
                → JSON response comes back
                  → the interceptor's error handler catches a 401 and
                    logs the user out automatically
```

### 5.3 Authentication, specifically

1. `POST /api/auth/login` with email+password → if correct, the
   backend returns `{ accessToken, refreshToken, email, role,
   employeeId, mustChangePassword }`. `accessToken` is a JWT valid for
   1 hour; `refreshToken` for 24 hours (`jwt.expiration-ms` /
   `jwt.refresh-expiration-ms` in `application.yml`).
2. The frontend's `AuthService` stores both tokens in `localStorage`
   and exposes the current user as an Angular signal.
3. Every subsequent request gets `Authorization: Bearer <accessToken>`
   attached by `auth.interceptor.ts` — this is the *only* place in the
   frontend that adds that header; individual services never do it
   themselves.
4. On the backend, `JwtAuthFilter` runs once per request, before
   Spring Security's normal authorization checks: it extracts the
   token, validates the signature and expiry (`JwtService`), and — if
   valid — tells Spring Security who's making the request and what
   role they have. Everything downstream (`SecurityConfig`'s URL
   rules, service-layer checks) relies on that.
5. If the access token expires mid-session, the backend returns 401,
   the frontend's interceptor catches it, clears the stored session,
   and redirects to `/login`. There's no silent refresh yet (a
   documented gap) — the user has to log in again rather than the
   frontend quietly using the refresh token.

### 5.4 What the backend never trusts the frontend to enforce

Every role-based restriction shown in the UI (a hidden nav link, a
disabled button, a route guard) is a **UX convenience only**. The
actual security boundary is always server-side — `SecurityConfig`'s
URL-level rules first, then each service's own visibility check for
anything more granular than "which role can hit this URL at all." A
frontend route guard hiding `/admin/users` from a non-SUPER_ADMIN user
doesn't stop a crafted direct API call — the backend's
`hasRole("SUPER_ADMIN")` on that endpoint does.

---

## 6. Frontend — `eems-frontend/`, folder by folder

```
eems-frontend/
├── angular.json                Build config: budgets, asset paths, dev-server proxy target
├── package.json                 Dependencies (Angular, Angular Material, Chart.js, ...)
├── Dockerfile                   Multi-stage: Node build → Nginx runtime
├── nginx.conf                   SPA fallback routing + /api reverse proxy (see section 4)
├── public/
│   ├── logo.svg                 App logo (also the favicon)
│   └── templates/                Downloadable CSV import templates (employees/departments/positions)
└── src/
    ├── environments/             environment.ts (dev) vs environment.prod.ts (Docker/prod)
    └── app/
        ├── app.routes.ts         Every route in the app, plus which guard(s) apply
        ├── app.config.ts         Global providers: router, HttpClient + the auth interceptor
        │
        ├── core/                 Nothing here renders UI - it's cross-cutting plumbing
        │   ├── models/           One .ts file per DTO shape, mirroring the backend's dto/ package
        │   ├── services/         One *ApiService per backend resource (thin HttpClient wrappers)
        │   ├── guards/           authGuard (must be logged in), roleGuard(...) (must have a role)
        │   └── interceptors/     auth.interceptor.ts - attaches the JWT, handles 401s
        │
        ├── features/              One folder per screen - each is a standalone Angular component
        │   ├── login/             Split-panel login with header/footer, Caps Lock warning, etc.
        │   ├── dashboard/         Role-scoped landing page with live widgets
        │   ├── employees/         List + Create/Edit form
        │   ├── employee-profile/  Detail view: core info + address + emergency contacts +
        │   │                      real file upload/download + offer letter PDF download
        │   ├── departments/       List + CSV import/export + delete
        │   ├── positions/         Create/edit (including job description) + CSV import/export + delete
        │   ├── job-postings/      Card-based browse/manage - visibility-scoped server-side
        │   ├── org-chart/         Reporting hierarchy tree, built client-side from
        │   │                      each employee's existing manager - no new endpoint
        │   ├── leave/             Submit/approve + "My Leave Balances"
        │   ├── leave-balances/    HR allocation management, bulk-allocate, carry-over
        │   ├── analytics/         HR analytics charts + PDF/Excel export + Power BI connect card
        │   ├── change-password/   SMS-confirmed two-step flow
        │   ├── admin/             User role grant/revoke, enable/disable
        │   └── audit-logs/        Paginated, filterable audit trail viewer
        │
        └── shared/
            ├── layout/            shell.component - the header/nav/footer wrapping every
            │                      authenticated route (everything except /login)
            └── chart/             A small Chart.js wrapper (ng2-charts needs Angular 21+;
                                   this project targets 18, so a custom wrapper avoided
                                   that version conflict entirely)
```

### The pattern every feature follows

Almost every screen is: a `*ApiService` in `core/services/` (talks to
one backend resource), a model in `core/models/` (matches the
backend's DTO shape), and a component in `features/<name>/` that
injects the service and renders the result. This repetition is
intentional — once you've read one feature end to end, you can predict
the shape of every other one.

---

## 7. Where to look next

- **A specific feature's behavior**: `eems-backend/README.md` and
  `eems-frontend/README.md` — both organized by feature, with a
  "What's implemented" / "Not yet implemented" list at the end of each.
- **How to run it** (local, Docker, or Power BI-connected): the root
  `README.md`.
- **Sample data for testing**: the CSV files generated alongside this
  project (departments → positions → employees, in that order).
