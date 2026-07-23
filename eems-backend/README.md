# EEMS Backend

Spring Boot implementation of the core modules from the SRS: Authentication & RBAC,
Employee Records, Department/Org Structure, and Leave Management. Performance
Review and the full GDPR module (DSAR export, erasure workflow, processing
register) are stubbed out in the SRS but not yet implemented here — see
"Not yet implemented" below.

## Requirements
- Java 17+
- Maven 3.9+
- (Optional) PostgreSQL 14+ for the `prod` profile — the `dev` profile runs
  entirely on an in-memory H2 database, no external DB needed.

> **Note:** this code was written and reviewed by hand and could not be
> compiled in the environment that generated it (no access to Maven
> Central there). It has since been compile-tested by a user and one real
> issue was found and fixed — see "Troubleshooting" below if you hit
> anything similar.

## Troubleshooting

**`cannot find symbol: method builder()` / `getX()` / `setX()` on every
entity** — Lombok's annotation processor isn't running. Some local
Maven/IDE setups don't auto-discover it from the classpath even though
it's a normal dependency. Fixed as of this version by explicitly wiring
Lombok into `maven-compiler-plugin`'s `annotationProcessorPaths` in
`pom.xml`. If you still see this after pulling the latest `pom.xml`:
- Run `mvn clean compile` (not just `compile`) to force a full recompile.
- If using an IDE (IntelliJ/Eclipse), make sure annotation processing is
  enabled in its build settings — the IDE's own compiler has a separate
  toggle from Maven's.
- Confirm you don't have a stale Lombok jar on your local `~/.m2`
  repository from a much older/incompatible version; delete
  `~/.m2/repository/org/projectlombok` and let Maven re-download it.

**`Fatal error compiling: java.lang.ExceptionInInitializerError:
com.sun.tools.javac.code.TypeTag :: UNKNOWN`** — this means annotation
processing *is* running, but your JDK is newer than the pinned Lombok
version supports. Lombok patches internal `javac` classes, so it lags
behind new JDK releases (this is a known, recurring issue for JDK
23/24/25 — Lombok tracks it upstream at
https://github.com/projectlombok/lombok/issues/3814). Fixed as of this
version by bumping `lombok.version` to `1.18.42` in `pom.xml`, which has
JDK 24/25 fixes. If it still happens on your machine:
- Check `java -version` — if you're on a very recent JDK (24+), an even
  newer Lombok patch release may be needed; check
  https://projectlombok.org/download for the latest.
- Simplest fix if you have multiple JDKs installed: point Maven at
  JDK 17 or 21 instead (`export JAVA_HOME=/path/to/jdk-21`), since
  that's what this project targets (`java.version` in `pom.xml`) and
  avoids bleeding-edge JDK/Lombok compatibility gaps entirely.

**`Table "LEAVE_BALANCE" not found` / `Syntax error in SQL statement
... expected "identifier"` on startup (dev/docker profile, H2)** — a
real bug, fixed as of this version: the `LeaveBalance` entity's `year`
field had no explicit `@Column` name, so Hibernate generated an
unquoted `year` column — and `YEAR` is a reserved keyword in H2's SQL
dialect (a date/time function), which H2's DDL parser rejects outright.
Fixed by mapping it to `@Column(name = "balance_year")` instead — the
Java property is still called `year` (so no service/DTO code needed to
change), only the physical database column name changed. The raw SQL
in `db/powerbi-reporting-views.sql`'s `vw_leave_balance` view was
updated to match (`lb.balance_year AS year`). If you're on an older
extraction of this project and still hit this, re-pull
`LeaveBalance.java` and the SQL views file.

**`404` on `GET /api/leave-balances/me` (or a 404 when a SUPER_ADMIN/
HR_ADMIN approves a leave request)** — a real bug, fixed as of this
version: several service methods looked up the current user's
`Employee` profile *unconditionally* and threw if none existed. That's
correct for actions that genuinely require being an employee (e.g.
submitting a leave request), but wrong for read-only "my own data"
endpoints and for HR/Admin actions — `admin@eems.local` (SUPER_ADMIN)
has no linked `Employee` record by design (see `DevDataSeeder`), so
this broke both "my leave balances" (now returns an empty list instead
of a 404 — `LeaveBalanceService.listForCurrentUser`) and approving/
rejecting a leave request as SUPER_ADMIN/HR_ADMIN (now looks up the
approver's `Employee` profile optionally — `LeaveRequest.approvedBy`
simply stays `null` when there isn't one, since that field was already
nullable — `LeaveService.decide`). If you're on an older extraction and
still hit either, re-pull `LeaveService.java` and
`LeaveBalanceService.java`.

**Bulk employee import fails on every row with "Unknown department" /
"Unknown position"** — not a bug, but a data-consistency requirement:
`EmployeeImportService` matches `departmentName`/`positionTitle` by
exact name against what already exists in the database. Import order
matters: **departments → positions → employees**, every time. If you
generated your own CSV files (e.g. via a different tool or LLM) with
department/position names that don't match what's already in your
database — including the ones `DevDataSeeder` creates automatically
(`Engineering`, plus its two starter positions) — every row referencing
an unrecognized name will fail with a per-row message naming exactly
what wasn't found. Create (or import) the missing departments/positions
first, using the *exact* names your employee file expects.

A related real bug, also fixed as of this version: `DepartmentService.
create` and `PositionService.create` had **no duplicate-name check** —
re-importing a department/position that already exists (e.g.
"Engineering", which `DevDataSeeder` already creates) would silently
create a *second* row with the same name instead of failing cleanly.
That's worse than it sounds: `findByNameIgnoreCase`/`findByTitleIgnoreCase`
(used by employee/position import to resolve a name) then throws
`IncorrectResultSizeDataAccessException` the next time anyone looks that
name up, since Spring Data expects 0-or-1 results, not 2. Both now
reject a duplicate name/title with a clear error instead.

**`mvn compile` fails with "constructor CreatePositionRequest ... cannot
be applied to given types"** — a real bug, fixed as of this version:
adding `jobDescription` to `PositionDtos.CreatePositionRequest` (see
"Position entity" above) wasn't matched by an update to every place
that constructs one — `PositionImportService.toCreateRequest` still
built it with only 4 arguments instead of 5. Fixed by adding the
missing argument (and, while touching that code, added `jobDescription`
as an optional bulk-import column too). If you're on an older
extraction and still hit this, re-pull `PositionImportService.java`.

**Every API call returns `403` after being logged in for a while,
even on endpoints that should work for any authenticated user (e.g.
`GET /api/employees`), and the frontend gets stuck showing a stale
"Signed in as..." header with no automatic redirect to login** — a
real bug, fixed as of this version. The access token expires after 1
hour (`jwt.expiration-ms`); once it does, `JwtAuthFilter` correctly
rejects it and leaves the request unauthenticated - but `SecurityConfig`
never configured a custom `AuthenticationEntryPoint`, and without
`httpBasic()`/`formLogin()` either, Spring Security's default fallback
(`Http403ForbiddenEntryPoint`) returns **403 for missing/expired/invalid
authentication, not just for a genuine role mismatch** - backwards from
the normal convention (401 = "who are you", 403 = "I know who you are,
but no"). The frontend's `auth.interceptor.ts` only listens for 401 to
trigger an automatic logout+redirect, so it never fired, leaving the
user stuck. Fixed by adding an explicit `.exceptionHandling(...)` with
a proper `AuthenticationEntryPoint` (401, "please log in again") and
`AccessDeniedHandler` (403, for real authorization denials) — this
restores the normal convention and makes the existing frontend
auto-logout logic work as originally intended, with no frontend change
needed. If you're on an older extraction and still hit this, re-pull
`SecurityConfig.java`, and just log out and back in once to get a
fresh token.

**`/h2-console` loads but the actual console UI inside it doesn't
render** (dev profile) — fixed as of this version. The `permitAll` rule
for `/h2-console/**` made the URL reachable without auth, but H2's
console renders itself inside an iframe internally, and Spring
Security's default `X-Frame-Options: DENY` blocked that frame
regardless — the page loaded, the actual console inside it didn't.
Fixed with `.headers(headers -> headers.frameOptions(frame ->
frame.sameOrigin()))`, which only relaxes frame-blocking for
same-origin frames (H2 console's own internal one) rather than
disabling clickjacking protection for the whole app.

## Running locally (dev profile, H2)

```bash
mvn spring-boot:run
```

The app starts on `http://localhost:8080` with `dev` as the active profile
and seeds three accounts (see console output on first run):

| Email | Role | Password |
|---|---|---|
| admin@eems.local | SUPER_ADMIN | ChangeMe123! |
| manager@eems.local | MANAGER | ChangeMe123! |
| employee@eems.local | EMPLOYEE (reports to manager@eems.local) | ChangeMe123! |

H2 console: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:eemsdb`).

## Running against PostgreSQL (prod profile)

```bash
export DB_URL=jdbc:postgresql://localhost:5432/eemsdb
export DB_USERNAME=eems_user
export DB_PASSWORD=your-password
export JWT_SECRET=$(openssl rand -base64 48)
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

`ddl-auto` is set to `validate` in `prod` — create the schema via a
migration tool (Flyway/Liquibase) before first run; this project currently
relies on Hibernate `create-drop` only in `dev` for convenience.

## Running via Docker (docker profile)

There's also a third profile, `docker` — real PostgreSQL like `prod`,
but `ddl-auto: update` instead of `validate`, so a containerized stack
works out of the box without a separate migration step first. See the
root `README.md` → "Docker" section for the full `docker compose up`
instructions; this profile isn't meant to be run manually outside that
context, though nothing stops you from pointing it at your own Postgres
via the same `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` env vars as `prod`.

## Auth flow

1. `POST /api/auth/login` with `{ "email": ..., "password": ... }` → returns
   `accessToken` (1h expiry) + `refreshToken` (24h expiry).
2. Send `Authorization: Bearer <accessToken>` on subsequent requests.
3. `POST /api/auth/refresh` with `{ "refreshToken": ... }` to get a new access token.

## Key endpoints

| Method | Path | Access |
|---|---|---|
| GET | `/api/admin/users` | SUPER_ADMIN only |
| PATCH | `/api/admin/users/{id}/role` | SUPER_ADMIN only (blocked for your own account — see below) |
| PATCH | `/api/admin/users/{id}/enabled` | SUPER_ADMIN only (blocked for your own account — see below) |
| POST | `/api/admin/users/{id}/reset-password` | SUPER_ADMIN only (blocked for your own account) |
| POST | `/api/auth/login` | Public |
| POST | `/api/employees` | SUPER_ADMIN, HR_ADMIN |
| GET | `/api/employees` | Any authenticated user (results scoped by role) |
| PUT | `/api/employees/{id}` | SUPER_ADMIN, HR_ADMIN, MANAGER (own reports only) |
| POST | `/api/employees/{id}/offboard` | SUPER_ADMIN, HR_ADMIN |
| POST/GET | `/api/departments` | Write: SUPER_ADMIN, HR_ADMIN. Read: any authenticated user |
| POST | `/api/leave-requests` | Any authenticated employee (submits for themself) |
| PATCH | `/api/leave-requests/{id}/decision` | SUPER_ADMIN, HR_ADMIN, MANAGER (direct manager only) |
| GET | `/api/audit-logs?entity=&actor=&page=&size=` | SUPER_ADMIN, AUDITOR (paginated, filterable) |
| PATCH | `/api/users/me/phone` | Any authenticated user (sets their own phone number) |
| POST | `/api/auth/change-password/initiate` | Any authenticated user (rate-limited: 5/hour per user) |
| POST | `/api/auth/change-password/confirm` | Any authenticated user |
| GET | `/api/reports/hr-summary` | SUPER_ADMIN, HR_ADMIN, AUDITOR |
| GET | `/api/reports/hr-summary/export.xlsx` | SUPER_ADMIN, HR_ADMIN, AUDITOR |
| GET | `/api/reports/hr-summary/export.pdf` | SUPER_ADMIN, HR_ADMIN, AUDITOR |
| POST | `/api/employees/import` | SUPER_ADMIN, HR_ADMIN (multipart CSV/XLSX) |
| GET | `/api/powerbi/employees` | SUPER_ADMIN, HR_ADMIN, AUDITOR |
| GET | `/api/powerbi/departments` | SUPER_ADMIN, HR_ADMIN, AUDITOR |
| GET | `/api/powerbi/leave-requests` | SUPER_ADMIN, HR_ADMIN, AUDITOR |
| POST/GET | `/api/positions` | Write: SUPER_ADMIN, HR_ADMIN. Read: any authenticated user |
| DELETE | `/api/departments/{id}` | SUPER_ADMIN, HR_ADMIN (blocked if still referenced) |
| DELETE | `/api/positions/{id}` | SUPER_ADMIN, HR_ADMIN (blocked if still referenced) |
| POST | `/api/departments/import` | SUPER_ADMIN, HR_ADMIN (multipart CSV/XLSX) |
| POST | `/api/positions/import` | SUPER_ADMIN, HR_ADMIN (multipart CSV/XLSX) |
| GET | `/api/departments/export.csv` | Any authenticated user |
| GET | `/api/positions/export.csv` | Any authenticated user |
| GET | `/api/employees/export.csv` | Any authenticated user (visibility-scoped, same as `GET /api/employees`) |
| GET | `/api/app-settings/employee-id-format` | Any authenticated user |
| PUT | `/api/app-settings/employee-id-format` | SUPER_ADMIN, HR_ADMIN |
| POST | `/api/leave-balances/bulk-allocate` | SUPER_ADMIN, HR_ADMIN |
| POST | `/api/leave-balances/carry-over` | SUPER_ADMIN, HR_ADMIN |
| GET/POST | `/api/employees/{id}/documents` | Self, direct manager, or HR/Admin/Auditor (metadata-only record) |
| POST | `/api/employees/{id}/documents/upload` | Same as above (real file, stored via `FileStorageService`) |
| GET | `/api/employees/{id}/documents/{docId}/download` | Same as above |
| GET | `/api/employees/{id}/salary` | Self or HR_ADMIN/SUPER_ADMIN only — not a manager |
| POST | `/api/employees/{id}/salary` | HR_ADMIN/SUPER_ADMIN only — not even self |
| GET/POST | `/api/employees/{id}/employment-contracts` | GET: self/manager/HR/Admin/Auditor. POST: HR_ADMIN/SUPER_ADMIN |
| GET | `/api/employees/{id}/offer-letter/export.pdf` | Self or HR_ADMIN/SUPER_ADMIN only — not a manager |
| GET | `/api/job-postings` | Any authenticated user — visibility-scoped in the service |
| POST/PUT/DELETE | `/api/job-postings/**` | SUPER_ADMIN, HR_ADMIN only |
| PUT | `/api/positions/{id}` | SUPER_ADMIN, HR_ADMIN (edit grade/salaryBand/jobDescription/department — title is immutable) |

## Admin role management (grant/revoke)

`AdminUserController`/`AdminUserService` — separate from `EmployeeService`
deliberately, since this operates on the `User`/auth identity, not the
Employee HR record. **SUPER_ADMIN only** — note this is *more*
restrictive than most of the other admin-ish endpoints in this codebase
(which allow HR_ADMIN too), because granting/revoking roles is a
privilege-escalation-sensitive operation; an HR_ADMIN shouldn't be able
to grant themselves SUPER_ADMIN. See the dedicated
`/api/admin/users/**` rule in `SecurityConfig`, listed *before* the
broader `/api/admin/**` rule (Spring Security is first-match-wins).

**Self-modification is blocked**: a SUPER_ADMIN can't change their own
role, disable their own account, or reset their own password this way,
even via direct API call — the first two are easy ways to accidentally
lock everyone out with no one left who can undo it, and the third
already has a proper dedicated flow (SMS-confirmed change-password).
`AdminUserService.guardAgainstSelfModification` throws a 403 for any of
the three. Use a second SUPER_ADMIN account if you genuinely need to
change the first two on the "self" account.

Every role change, enable/disable, and password reset is written to the
audit log (`ROLE_CHANGED`, `ACCOUNT_ENABLED`, `ACCOUNT_DISABLED`,
`PASSWORD_RESET_BY_ADMIN`).

## Admin-generated temporary credentials

Two places now generate secure credentials server-side instead of using
a fixed placeholder password:

**1. Creating an employee** (`POST /api/employees`) — `email` and
`initialPassword` are both optional. Leave either blank and
`CredentialGenerator` fills it in:
- Email: `firstname.lastname@eems.local`, with a numeric suffix
  (`.2`, `.3`, ...) if that's already taken.
- Password: a random 12-character string guaranteed to contain an
  uppercase letter, lowercase letter, digit, and symbol, with
  visually-ambiguous characters (0/O, 1/l/I) excluded since a human
  needs to read it off a screen and type it. The account is flagged
  `mustChangePassword = true` — but **only** when the password was
  generated; if the caller supplies their own `initialPassword`
  explicitly, that choice is respected and no forced change happens.

The response (`CreateEmployeeResponse`) includes `generatedEmail`/
`generatedTemporaryPassword` **only** when those values were actually
generated (both `null` otherwise) — this is the one and only place the
plaintext temporary password is ever returned. It isn't stored anywhere
and can't be retrieved again after this response.

**2. Resetting an existing user's password** (`POST
/api/admin/users/{id}/reset-password`, SUPER_ADMIN only) — same
generator, same one-time plaintext response (`PasswordResetResponse`),
same `mustChangePassword` flag. Useful for a manager who's forgotten
their password or needs fresh credentials issued.

**Bulk employee import** (`EmployeeImportService`) also switched from a
hardcoded default password to this same generator — if a row's
`initialPassword` column is blank, a secure temporary password is
generated per-row and surfaced in that row's result message (e.g.
`"Created (temp password: xY7!kLp2Rt)"`), rather than every imported
employee sharing one fixed, predictable password.

**Enforcement on login**: `AuthResponse` now includes
`mustChangePassword`. The frontend checks this and redirects straight to
`/change-password` instead of `/dashboard` when true. **This is a UX
redirect, not a hard block** — there's no route guard preventing
navigation elsewhere afterward if the user manually changes the URL; a
real enforcement mechanism (e.g. rejecting non-change-password API calls
while the flag is set) is a reasonable next step if this matters for
your use case. The flag clears automatically once the user successfully
completes the normal SMS-confirmed change-password flow
(`PasswordChangeService.confirm`).

## Leave balance calculator + management

Real balance tracking, not just a display: `LeaveBalanceService` computes
**allocated − used − pending** per employee, leave type, and calendar
year, and is the single source of truth both for validating new
requests and for deducting balance on approval.

**Only ANNUAL, SICK, and PARENTAL are balance-tracked**
(`LeaveBalanceService.BALANCE_TRACKED_TYPES`) — UNPAID and OTHER aren't
capped, matching how most real HR systems treat them (unpaid leave has
no limit by definition; OTHER is a catch-all). Setting an allocation
for either via the API throws a clear error rather than silently doing
nothing.

**Enforcement happens at submission, not approval**:
`LeaveService.submit` calls `LeaveBalanceService.validateRequest` before
the request is even persisted — an over-request never reaches PENDING.
"Pending" balance is computed live by summing an employee's current
PENDING requests of that type/year, rather than stored as a separate
reserved/held column — so it can never drift out of sync, and a
rejected or cancelled request automatically frees up the balance it was
holding without any extra bookkeeping. On approval,
`LeaveBalanceService.applyApproval` increments `usedDays` permanently.

**Endpoints:**

| Method | Path | Access |
|---|---|---|
| GET | `/api/leave-balances/me?year=` | Any authenticated user (own balances) |
| GET | `/api/leave-balances/employee/{id}?year=` | Visibility-checked in the service — same rule as `EmployeeService` (self, direct manager, or HR/Admin/Auditor) |
| GET | `/api/leave-balances?year=` | SUPER_ADMIN, HR_ADMIN, AUDITOR (org-wide overview) |
| POST | `/api/leave-balances` | SUPER_ADMIN, HR_ADMIN (set/update one employee's allocation) |
| POST | `/api/leave-balances/bulk-allocate` | SUPER_ADMIN, HR_ADMIN (same allocation → every active employee, optionally scoped to one department) |
| POST | `/api/leave-balances/carry-over` | SUPER_ADMIN, HR_ADMIN (roll each employee's unused fromYear balance onto toYear, optionally capped) |

`year` defaults to the current calendar year if omitted on any GET.
`carry-over` **adds** the carried amount to whatever `toYear` allocation
already exists rather than overwriting it — safe to run after
`bulk-allocate` has already set a base allocation for the new year.

**A known gap worth naming**: if two requests of the same type are
submitted in quick succession, both are validated against the balance
*at the moment each is submitted* — there's no database-level locking
preventing a race where both pass validation before either is
persisted. Low-risk for a single-employee self-service flow (a person
isn't usually submitting two leave requests simultaneously), but a real
concern if this were ever driven by concurrent automated clients.

## Audit log read API

`AuditLogController` exposes the append-only log `AuditService` has
been writing to since the very first version of this app —
`GET /api/audit-logs?page=&size=&entity=&actor=` (SUPER_ADMIN, AUDITOR),
paginated (capped at 100/page regardless of what's requested) and
optionally filtered by entity type (`Employee`, `LeaveRequest`, `User`,
`LeaveBalance`, ...) and/or actor email. Ordered newest-first.

## Rate limiting on password-change requests

`RateLimiter` (`com.eems.security`) is a basic in-memory sliding-window
limiter — 5 `change-password/initiate` attempts per user per hour,
returning 429 once exceeded. Deliberately simple (no Redis, no external
dependency), which also means it **only works correctly for a single
backend instance** — a horizontally-scaled deployment would need a
shared store instead, since each instance would otherwise track its own
independent counter. This limits how often someone can *request* a new
OTP; the OTP itself already had its own separate attempt limit
(5 guesses per code, see `PasswordChangeService`).

## Delete for Departments and Positions

Hard delete, guarded by referential-integrity checks rather than a
soft-delete flag: `DepartmentService.delete` and `PositionService.delete`
refuse cleanly (with a clear message naming what's still attached)
rather than letting a raw database FK-constraint error leak through —
a department can't be deleted while employees, positions, or child
departments still reference it; a position can't be deleted while
employees still hold it.

## Employee ID generator (prefix/suffix based)

`Employee.employeeCode` (e.g. `EMP-0007`) is a human-readable ID,
distinct from the internal database `id` — generated once, at creation
time, by `AppSettingsService.generateNextEmployeeCode()` using whatever
prefix/suffix/sequence was configured in `AppSettings` at that moment.
`AppSettings` reuses the same singleton-row pattern as the company
logo, extended with `employeeIdPrefix` (default `EMP-`),
`employeeIdSuffix` (default empty), and `nextEmployeeIdSequence`
(default `1`, always incremented, never reused — even if an employee is
later deleted, so two employees can never collide from this mechanism
alone).

| Method | Path | Access |
|---|---|---|
| GET | `/api/app-settings/employee-id-format` | Any authenticated user — not sensitive |
| PUT | `/api/app-settings/employee-id-format` | SUPER_ADMIN, HR_ADMIN |

**Changing the prefix/suffix only affects employees created *after* the
change** — existing `employeeCode`s are never regenerated or renamed,
since that could silently invalidate an ID someone's already written
down or referenced elsewhere.

**A known, honestly-documented limitation**: `generateNextEmployeeCode()`
reads the current sequence, then writes back the incremented value,
without a pessimistic lock on the settings row. Two employees created
in the same split-second by genuinely concurrent requests could
theoretically read the same sequence value before either write
commits, producing a duplicate code — the `employee_code` unique
constraint would catch that as a save failure (not silently corrupt
data), but the second request would simply fail rather than gracefully
retry with the next number. Fine for a single HR admin creating
employees one at a time (the realistic use case here); a genuinely
high-concurrency deployment would want a real DB sequence or an
explicit pessimistic lock instead.

## Employee search (name, email, employee ID)

`EmployeeResponse` now includes `employeeCode` and `email` (the latter
via `employee.getUser().getEmail()` — `User` is a lazy `@OneToOne` on
`Employee`, so this needed the same fetch-join treatment as the N+1 fix
above; see the frontend README for the actual search UI, which filters
client-side against the already-fetched list rather than adding a new
server-side search endpoint).

**A mistake caught and fixed in the same pass it was introduced**:
adding `email` to `toResponse()` touched `Employee.user`, another lazy
relationship, inside the exact per-row mapping loop that had just been
fixed for N+1 queries. `user` was added to both fetch-joined
`EmployeeRepository` queries (`findAllWithRelations()`,
`findByManagerIdWithRelations()`) before this shipped — worth calling
out explicitly, since it's a concrete example of why this class of bug
tends to recur: every new field added to a bulk-mapped response needs
the same scrutiny, not just the first pass.

## Company logo (SUPER_ADMIN only)

`AppSettings` is a genuine single-row table (always `id=1`, fetched-or-
created by `AppSettingsService.getOrCreate`) — deliberately its own
tiny entity rather than folding into an existing one, since more
app-wide settings beyond just the logo are a likely next addition.
Reuses `FileStorageService` directly via a new generic (non-employee)
storage bucket (`storeAppSetting`/`loadAppSetting`/`deleteAppSetting`),
rather than a separate storage mechanism.

| Method | Path | Access |
|---|---|---|
| GET | `/api/app-settings/logo/status` | **Public** — no auth required |
| GET | `/api/app-settings/logo` | **Public** — streams the actual image bytes |
| POST | `/api/app-settings/logo` | SUPER_ADMIN only |
| DELETE | `/api/app-settings/logo` | SUPER_ADMIN only — reverts to the default `logo.svg` |

**The GET endpoints are deliberately public/unauthenticated** — the
login page needs to show the company's logo before anyone has
authenticated at all. This is the one place in the app where reading
data requires no token; writing to it is still strictly SUPER_ADMIN
only, enforced by dedicated `SecurityConfig` rules for `POST`/`DELETE`
on this exact path (listed as their own rule, not folded into a
broader pattern, so there's no ambiguity about scope).

## Employee profile photo

A real upload, reusing `FileStorageService` directly (same disk-backed
storage as document uploads) rather than a stub. The reference lives
directly on `Employee` (`photoStoredFileName`/`photoContentType`), not
a separate table — it's a genuine 1:1 "current photo," not a history
like documents.

| Method | Path | Access |
|---|---|---|
| GET | `/api/employees/{id}/photo/status` | Self, direct manager, or HR/Admin/Auditor |
| GET | `/api/employees/{id}/photo` | Same — streams the actual image bytes |
| POST | `/api/employees/{id}/photo` | Same — multipart, image content-types only |
| DELETE | `/api/employees/{id}/photo` | Same |

Uploading a new photo **replaces** the previous one (old file deleted
from disk first) rather than accumulating — unlike documents, which
keep every upload as its own record. Needed the same `SecurityConfig`
override as address/documents (`/api/employees/*/photo/**` →
`authenticated()`, listed before the general HR-only `POST
/api/employees/**` rule) so an employee can upload their own photo.

## Employee database extension (address, emergency contacts, documents, salary, contracts)

The normalized schema described in `ARCHITECTURE.md` → section 3 is now
backed by real endpoints, not just entities/repositories:

| Resource | Endpoints | Access |
|---|---|---|
| Address (1:1) | `GET/PUT /api/employees/{id}/address` | Self, direct manager, or HR/Admin/Auditor |
| Emergency contacts (1:N) | `GET/POST /api/employees/{id}/emergency-contacts`, `DELETE .../{contactId}` | Same as above |
| Documents (1:N) | `GET/POST /api/employees/{id}/documents` (metadata-only record), `POST .../documents/upload` (real file), `GET .../documents/{documentId}/download`, `DELETE .../{documentId}` | Same as above |
| Salary — **effective-dated history** (1:N) | `GET /api/employees/{id}/salary`, `POST /api/employees/{id}/salary` | GET: self or HR_ADMIN/SUPER_ADMIN only — **not** a direct manager, unlike everything else. POST: HR_ADMIN/SUPER_ADMIN only, not even self. |
| Employment contracts (1:N) | `GET/POST /api/employees/{id}/employment-contracts`, `DELETE .../{contractId}` | GET: self/manager/HR/Admin/Auditor. POST/DELETE: HR_ADMIN/SUPER_ADMIN only (enforced by the general `POST/DELETE /api/employees/**` rule, not a separate one). |

## Real file upload/download (FileStorageService)

Documents now support two ways of getting a record: a metadata-only
entry (`POST /api/employees/{id}/documents`, just a `fileUrl` pointing
somewhere external) or **a real upload** (`POST
/api/employees/{id}/documents/upload`, multipart) whose actual bytes
`FileStorageService` writes to local disk — genuinely written and read
from disk, not a stub. Files live under `file.upload-dir`
(`application.yml`, defaults to `/tmp/eems-uploads`), one subfolder per
employee, with a UUID-prefixed on-disk filename (so two uploads named
`cv.pdf` never collide) while the original filename is preserved
separately for display/download. `GET .../documents/{id}/download`
streams a real upload back; it 400s if you call it on a metadata-only
record (there's nothing to stream).

**This is local disk, not S3/MinIO/Azure Blob** — fine for a single
backend instance with a persistent volume (see `docker-compose.yml`,
which mounts a named volume `eems_uploads` at this path so files
survive a container restart), but won't survive a container being
recreated *without* that volume, and won't work at all if this app is
ever horizontally scaled to multiple instances (each would have its own
disk). Migrating to real object storage later would mean swapping
`FileStorageService`'s implementation — its `store()`/`load()`/
`delete()` method signatures wouldn't need to change.

**Salary's access rule is deliberately stricter** than every other
employee sub-resource: a MANAGER can see a direct report's address,
emergency contacts, and documents, but *not* their salary history —
only the employee themself or HR_ADMIN/SUPER_ADMIN. Creating a new
salary record automatically closes out the previous "current" one
(the row with `effectiveTo = null`) by setting its `effectiveTo` to the
day before the new record's `effectiveFrom` — so there's never more
than one open-ended salary record per employee, and past compensation
stays visible rather than being overwritten. `taxNumber`/`bankName`/
`iban` carry the same "should be encrypted at rest via a KMS-backed
converter in production" caveat as `nationalId` — not implemented here,
for the same reason (no real KMS available in this environment).

A `SecurityConfig` subtlety worth calling out: address/emergency-contacts/
documents needed a **new** rule (`/api/employees/*/address/**` etc. →
`authenticated()`) placed *before* the general `POST/PUT/DELETE
/api/employees/**` rules, since an EMPLOYEE managing their own address
would otherwise be blocked by the broader HR/Admin-only rule before
ever reaching the service layer's "self is fine" check. Salary and
employment-contracts didn't need this — their creation/deletion access
requirements happen to already match the general rule exactly
(HR_ADMIN/SUPER_ADMIN only, no MANAGER).

## Position entity (normalized schema, first slice)

`Employee.jobTitle` (a free-text string) has been replaced with
`Employee.position` — a proper foreign key to a new `Position` entity
(`title`, `grade`, `salaryBand`, `jobDescription`, `department`). This
was the first concrete step toward a normalized enterprise HR schema
instead of one flat `employees` table — see "Employee database
extension" above for how much further that's gone since, and "What's
implemented" below for what's still deliberately not built (attendance,
performance reviews, training).

Manage positions via `POST/GET /api/positions` (same read/write access
pattern as departments), plus **`PUT /api/positions/{id}`** to edit
`grade`/`salaryBand`/`jobDescription`/`departmentId` after creation —
`title` is deliberately NOT editable there, since it's what CSV import
and `findByTitleIgnoreCase` match against; renaming it would silently
break anything that referenced the old title. `jobDescription` is a
free-text field (responsibilities, requirements) surfaced on both the
Positions page and any job posting linked to that position (see "Job
postings" below).

`EmployeeImportService`'s CSV/XLSX column is now
`positionTitle`, not `jobTitle` — it must match an existing position's
title exactly (case-insensitive), same convention as `departmentName`.

**Position (job title) is deliberately separate from Role (app
permissions)** — this matters for anyone adding organizational titles
like CEO, Director, Team Leader, etc. `Position` describes where
someone sits in the org chart; `Role` (`SUPER_ADMIN`/`HR_ADMIN`/
`MANAGER`/`EMPLOYEE`/`AUDITOR`, on `User`, not `Employee`) controls
what they can actually do in the app. A Director doesn't automatically
get HR-admin permissions from their title, and a Team Leader isn't
automatically an app-level `MANAGER` — the two are assigned
independently (a real CEO might reasonably still be app Role
`EMPLOYEE` if they don't need to manage HR data directly). The
frontend's Org Chart page (see `eems-frontend/README.md`) visualizes
the reporting hierarchy using Position titles, built entirely from
`Employee.manager` — no separate org-chart data model needed.

## Department bulk import + export

Mirrors the employee import pattern, supporting both CSV and XLSX:

- **`POST /api/departments/import`** — columns `name, location,
  parentDepartmentName` (optional). Rows are processed top to bottom, so
  a parent department can be created earlier in the same file.
- **`GET /api/departments/export.csv`** — `departmentId, departmentName,
  location, activeHeadcount, totalHeadcount` for every department,
  reusing the same flat rows the Power BI feed already computes
  (`PowerBiService.departmentRows()`).

## Position bulk import

Same pattern again, now supporting both CSV and XLSX (matching
employee/department import): **`POST /api/positions/import`** — columns
`title, grade, salaryBand, jobDescription, departmentName` (all but
`title` optional). `departmentName`
must match an existing department exactly (case-insensitive) — import
departments first if a row references one that doesn't exist yet. This
exists specifically to make seeding realistic demo data practical: with
dozens of positions to create, one-at-a-time form submission on the
Positions page isn't a reasonable way to set them up. A CSV export
(`GET /api/positions/export.csv`) is available too, matching departments.

## Job postings (Internal/External)

A real, standalone recruitment-adjacent feature — deliberately scoped
to just the posting itself, not a full applicant tracking system. There
is no Candidate/Application/Interview data model here; that would be a
genuine ATS subsystem of its own (see "Not yet implemented" below for
why an interview rubric specifically was left out this round — a
rubric with nothing to score against is a shell).

`JobPosting` (`title`, `description`, optional `department`/`position`
links, `visibility`, `status`, `location`, `postedDate`, `closingDate`):

| Method | Path | Access |
|---|---|---|
| GET | `/api/job-postings` | Any authenticated user — **visibility-scoped in `JobPostingService`**: SUPER_ADMIN/HR_ADMIN/AUDITOR see every posting regardless of status/visibility; everyone else only sees `status=OPEN` postings with `visibility=INTERNAL` or `BOTH` — never a `DRAFT`, never `CLOSED`, never `EXTERNAL`-only. |
| GET | `/api/job-postings/{id}` | Any authenticated user (not further scoped — same as the list, just not filtered; fine for a single lookup by id, but note this means an unscoped detail fetch isn't blocked the way the list is) |
| POST/PUT/DELETE | `/api/job-postings/**` | SUPER_ADMIN, HR_ADMIN only |

`visibility=EXTERNAL` is a **label for HR's own tracking** of where a
role is being advertised (LinkedIn, a job board, etc.) — this app has
no public/unauthenticated career page that would actually publish
anything there. If a real external-facing careers site is ever wanted,
it would need its own unauthenticated read endpoint (carefully scoped
to `OPEN` + `EXTERNAL`/`BOTH` only) rather than reusing this
authenticated one.

## Offer letter generation

`GET /api/employees/{id}/offer-letter/export.pdf` generates a genuine
PDF (word-wrapped paragraphs via PDFBox, not a template file) from that
employee's existing position, department, and most recent salary record
(if one exists — the compensation paragraph is simply omitted
otherwise). Same access rule as Salary itself (self or HR_ADMIN/
SUPER_ADMIN only, not a manager), since the letter can include a
compensation figure. The content is generic boilerplate — there's no
e-signature, benefits catalog, or legal review workflow behind it -
treat the output as a draft to review and customize, not something to
send to a candidate unedited.

## Power BI integration

**No real Power BI Embedded integration is included.** Embedding a report
inside the app itself requires an Azure AD app registration and a Power
BI Pro/Premium tenant with service-principal credentials — none of which
were available to provision or test here. What's included instead are
the two ways teams actually feed Power BI from an application like this:

**1. Direct PostgreSQL connection (recommended for production/scheduled refresh)**

Run `db/powerbi-reporting-views.sql` against your `prod` database (after
the app has created its tables — `ddl-auto` is `validate` in prod). It
creates:
- Four flattened views: `vw_employee_summary`, `vw_department_headcount`, `vw_leave_summary`, `vw_leave_balance` — joins done once in SQL instead of in every report's DAX.
- A dedicated read-only `eems_reporting_role` with `SELECT` only on those views (not the underlying tables, and not able to write anything) — **change its placeholder password before use**.

In Power BI Desktop: **Get Data → PostgreSQL database** → host/port/db
name → sign in as `eems_reporting_role` → select the three views. This
is the right choice for Power BI **Service** scheduled refresh, since it
doesn't depend on a short-lived bearer token.

**2. Flat REST/JSON endpoints (quick Power BI Desktop testing)**

`/api/powerbi/employees`, `/api/powerbi/departments`,
`/api/powerbi/leave-requests` mirror the same three views' shape, but
over this API — handy if you don't have (or don't want to grant) direct
DB access, or you're testing against the H2 `dev` profile where the SQL
views don't apply. In Power BI Desktop: **Get Data → Web**, paste the
URL, and add the current access token as an
`Authorization: Bearer <token>` HTTP header credential (the frontend's
Analytics page has a "Copy access token" button for this). The access
token expires in about an hour (`jwt.expiration-ms` in
`application.yml`), so this path is better for one-off pulls than for
Power BI Service's unattended scheduled refresh — use option 1 for that.

Both paths return identical column names/shapes so a report built against
one can be pointed at the other later with minimal rework.

## Fixed: N+1 queries slowing down list/report endpoints as headcount grows

A real, significant performance bug, fixed as of this version — noticed
once headcount grew past the seeded 2-3 employees into the hundreds via
CSV import. Several endpoints mapped `Employee`/`LeaveRequest`/
`JobPosting` entities to flat response rows by reading their
`@ManyToOne(fetch = FetchType.LAZY)` relationships (`department`,
`position`, `manager`, `employee`, `approvedBy`) directly — each of
those lazy relationships that hasn't already been loaded triggers its
**own separate SQL query** the first time it's accessed. Doing that
inside a loop over every row (`allEmployees.stream().map(this::toResponse)`)
means the total query count grows **linearly with the number of rows**,
not a fixed handful of queries regardless of size — the textbook "N+1
query problem." With 100 employees, `GET /api/employees` alone could
mean roughly 300 extra individual round-trips to the database (one for
department, one for position, one for manager, *per employee*), on
top of the one query that fetched the list itself.

**Fixed** by adding fetch-joined repository queries (`LEFT JOIN FETCH`)
that pull an entity and everything its response mapping needs in a
single query, and switching every affected call site to use them:

| Repository | New method | Fixes |
|---|---|---|
| `EmployeeRepository` | `findAllWithRelations()` (department + position + manager) | `EmployeeService.listVisibleTo` (SUPER_ADMIN/HR_ADMIN/AUDITOR branch), `ReportService.generateHrSummary`, `PowerBiService.employeeRows`/`departmentRows` |
| `EmployeeRepository` | `findByManagerIdWithRelations(managerId)` | `EmployeeService.listVisibleTo` (MANAGER branch) |
| `LeaveRequestRepository` | `findAllWithRelations()` (employee + employee.department + approvedBy) | `LeaveService.listForCurrentUser` (list-everyone branch), `PowerBiService.leaveRequestRows` — this one was actually the deepest N+1, two lazy hops per row (`leaveRequest → employee → department`) |
| `JobPostingRepository` | `findAllWithRelations()` (department + position) | `JobPostingService.list` — lower impact in practice since job postings will typically be a much smaller table than employees, but same class of bug, fixed for consistency |

Fetching all the relationships a query might need in one `LEFT JOIN
FETCH` costs nothing extra over fetching just one — it's still a
single round-trip either way — which is why there's one consolidated
`findAllWithRelations()` per repository rather than several narrower
variants. **This pattern doesn't automatically extend to new code** —
any new method that maps a list of entities with lazy relationships to
response DTOs needs its own fetch-joined query (or an `@EntityGraph`)
the same way; there's no global fix that prevents this class of bug
from recurring elsewhere.

## Change password (SMS-confirmed)

Two-step flow, both steps require the caller to already be authenticated:

1. **`POST /api/auth/change-password/initiate`** — body: `{ "currentPassword": "...", "newPassword": "..." }`.
   Verifies the current password, then sends a 6-digit code to the
   user's phone via `SmsSender` (5-minute expiry). The new password is
   hashed and held in a `PasswordChangeRequest` row — it does **not**
   take effect yet.
2. **`POST /api/auth/change-password/confirm`** — body: `{ "otpCode": "123456" }`.
   Verifies the code (max 5 attempts, single-use, expires) and only
   then copies the pending hash onto the user's real password.

A user must have a phone number on file first:
**`PATCH /api/users/me/phone`** — body: `{ "phoneNumber": "+491701234567" }` (E.164 format).

**No real SMS provider is wired up.** `ConsoleSmsSender`
(`com.eems.sms`) logs the OTP to the application console instead of
sending an actual text — there was no live provider account/credentials
available to integrate one here. To go live, implement `SmsSender`
against your provider's SDK (Twilio, Vonage, AWS SNS, etc.) and mark it
`@Primary`, or remove `ConsoleSmsSender`. Nothing else in the codebase
needs to change. All seeded dev accounts have placeholder phone numbers
so you can try the full flow locally — watch the console output after
calling `initiate`.

## HR analytics dashboard + report export

**`GET /api/reports/hr-summary`** computes standard HR analytics metrics
on the fly from existing data — no separate reporting database or ETL:
headcount (active + all statuses, by department, by status), average
tenure, new hires and offboards in the last 12 months, an approximate
attrition rate, and leave-request breakdowns by status and type. It
deliberately has no demographic/protected-characteristic fields — those
don't exist anywhere in the `Employee` entity.

Two export formats of the same data:
- **`GET /api/reports/hr-summary/export.xlsx`** — multi-sheet workbook (Overview, Headcount by Department, Leave) via Apache POI.
- **`GET /api/reports/hr-summary/export.pdf`** — paginated PDF via Apache PDFBox (`com.eems.report.HrSummaryPdfExporter` has a small hand-rolled cursor since PDFBox has no built-in text flow/pagination).

## Employee bulk import + export

**`POST /api/employees/import`** (multipart, field name `file`) accepts a
`.csv` or `.xlsx` file with a header row (case-insensitive, any column
order): `firstName, lastName, email, positionTitle, departmentName, hireDate
(yyyy-MM-dd), initialPassword` (both `email` and `initialPassword` are
optional — a blank `email` gets a generated `firstname.lastname@eems.local`
address, a blank `initialPassword` gets a secure random temporary
password via `CredentialGenerator`, same as the single-create API — see
"Admin-generated temporary credentials" above; there is no hardcoded
default password anywhere in this codebase).
`departmentName` and `positionTitle` must each match an existing
department/position exactly (case-insensitive) or that row fails with a
clear message — create positions via `POST /api/positions` first if a
row references one that doesn't exist yet. `managerId`
isn't supported via import — assign managers afterward through the UI.
Every row is processed independently and reuses `EmployeeService.create`,
so validation (duplicate email, etc.) is identical to the single-create
API. The response reports per-row success/failure so partial imports are
visible rather than silently dropped.

**`GET /api/employees/export.csv`** — `id, firstName, lastName,
positionTitle, departmentName, managerName, hireDate, exitDate, status`
for every employee visible to the caller (same visibility scoping as
`GET /api/employees` — a MANAGER exports their own reports, a plain
EMPLOYEE exports just their own row). Deliberately the same columns
`EmployeeResponse` already exposes over the API — no `nationalId` or
other sensitive fields.

## What's implemented vs. SRS scope

**Implemented:**
- JWT auth with access/refresh tokens, BCrypt password hashing
- RBAC at both the route level (`SecurityConfig`) and record level (manager-scoped visibility in `EmployeeService`/`LeaveService`)
- Employee CRUD + onboarding/offboarding status transitions, with a guard against setting an employee as their own manager
- Department/Position CRUD, including delete (blocked with a clear error if still referenced by employees, positions, or — for departments — child departments, rather than a raw FK-constraint error)
- Leave request submission + manager/HR approval workflow
- Append-only audit logging on create/update/view/decision actions, plus a paginated, filterable **read API** (`GET /api/audit-logs?entity=&actor=&page=&size=`, SUPER_ADMIN/AUDITOR)
- Change password with SMS one-time-code confirmation (SMS delivery is a console-logging stand-in — see above), with a `RateLimiter` capping `change-password/initiate` at 5 attempts/hour per user
- HR analytics summary (headcount, tenure, attrition, leave utilization) with PDF and Excel export
- Bulk employee/department/position import — all three now support both CSV and XLSX
- Power BI data feed: SQL reporting views for direct PostgreSQL connection, plus flat REST endpoints for Power BI Desktop's Web connector (no real Power BI Embedded/Azure AD integration — see "Power BI integration" above)
- Docker: multi-stage `Dockerfile` (Maven build → JRE runtime, non-root user), plus a `docker` Spring profile for the compose stack — see root `README.md` → "Docker" (unverified in the environment that wrote it — no Docker/registry access there)
- Position as a separate entity (replaces free-text `jobTitle`) — the first concrete slice of a normalized HR schema; full CRUD via `/api/positions`
- Department and Position bulk CSV/XLSX import + CSV export
- Leave balance calculator + management: allocated/used/pending/available tracking for ANNUAL/SICK/PARENTAL, enforced at submission, deducted on approval, plus bulk-allocate (one allocation → every active employee, optionally scoped to a department) and year-to-year carry-over of unused days
- Employee database extension: address (1:1), emergency contacts (1:N), documents (1:N, real file upload/download via local-disk `FileStorageService`, or metadata-only with an external link), effective-dated salary history (1:N, deliberately stricter access than everything else — self + HR/Admin only, no manager), and employment contracts (1:N) — see "Employee database extension" above
- Employee profile photo — real upload/download via `FileStorageService`, replaces rather than accumulates like documents do (a genuine "current photo," not a history)
- Company logo (SUPER_ADMIN only) — public read (the login page needs it pre-auth), restricted write, reusing `FileStorageService` via a new generic non-employee storage bucket
- Employee ID generator — configurable prefix/suffix (SUPER_ADMIN/HR_ADMIN), auto-generated per employee at creation, never retroactively changed for existing employees
- Employee search by name/email/employee ID — `email` and `employeeCode` added to `EmployeeResponse`
- Job description on Position (`jobDescription`, editable via `PUT /api/positions/{id}` — Position's first update capability; previously create/delete only)
- Job postings (Internal/External) — a scoped recruitment feature (posting management only, no candidate/application/interview data model) with visibility-scoped read access — see "Job postings" above
- Offer letter PDF generation — genuine word-wrapped PDF from real Employee/Position/Salary data, same access rule as Salary — see "Offer letter generation" above
- Admin role grant/revoke + account enable/disable, SUPER_ADMIN only, with a self-modification safeguard
- Server-generated temporary credentials (email + secure random password) for new employees and admin-triggered password resets, replacing the old hardcoded default password, with a `mustChangePassword` flag enforced (as a UX redirect, not a hard block - see "Admin-generated temporary credentials" above)

**Not yet implemented (natural next steps):**
- Interview rubric / a real applicant tracking system: Candidate, Application, and Interview entities, with a rubric (criteria + scoring scale) attached to an interview. Deliberately not built alongside job postings — a rubric with nothing to score against is a shell, and this is a genuine ATS subsystem of its own, not a quick add.
- A real external/public careers page — job postings currently require authentication to view at all (visibility-scoped by role, not by anonymous public access); a true external-facing board would need its own unauthenticated, carefully-scoped read endpoint.
- The rest of a fully normalized enterprise schema beyond what's now built (address, emergency contacts, documents, salary history, employment contracts — see "Employee database extension" above): `attendance` clock-in/out, `performance_reviews`, `training`. Each is a genuine subsystem; happy to tackle any of these next, one at a time, with the same care.
- Performance review module (section 3.5 of the SRS)
- Full GDPR module: consent tracking, DSAR export endpoint, erasure/anonymization workflow, Art. 30 processing register (section 3.7)
- Attendance clock-in/out (section 3.4, beyond leave)
- Real column-level encryption for `nationalId` (currently stored as a plain column — wire up a JPA `AttributeConverter` with a KMS-backed key before using with real data) — needs a real KMS/cloud provider integration, not available here
- Flyway/Liquibase migrations for the `prod` profile
- A real SMS provider integration (currently console-logging stand-in — see "Change password" above) — needs live Twilio/Vonage-style credentials, not available here
- Hard enforcement of `mustChangePassword` (currently a frontend redirect on login only - no backend route guard blocks other API calls while the flag is set; a user who manually navigates elsewhere after being redirected isn't stopped)
- Historical trend data for analytics (current metrics are point-in-time snapshots computed from current records, not tracked over time — e.g. attrition rate is approximated from current + reconstructed headcount, not from a stored time series)
- Import support for manager assignment and national ID (deliberately excluded from bulk import for now — see above)
- Real Power BI Embedded (in-app report embedding) — needs Azure AD app registration and Power BI Pro/Premium tenant credentials not available here; see "Power BI integration" above for the two working alternatives
- Concurrency protection on leave balance validation (see "A known gap worth naming" in "Leave balance calculator + management" above — two simultaneous submissions of the same type could both pass validation before either is persisted)
- Rate limiting is in-memory only (`RateLimiter` javadoc) — correct for a single backend instance, but a horizontally-scaled deployment would need a shared store (Redis) instead
