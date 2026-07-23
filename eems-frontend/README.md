# EEMS Frontend

Angular implementation of the core modules: login, role-scoped dashboard,
employee list, department management (HR/Admin only), and leave
submission/approval. Built and verified with `ng build --configuration
production` in the environment that generated it.

## Requirements
- Node.js 18+
- npm 9+

## Setup

```bash
npm install
```

## Run against the backend

Make sure the backend is running (see `../eems-backend/README.md`, dev
profile listens on `http://localhost:8080`), then:

```bash
npm start
# or: ng serve
```

The app runs on `http://localhost:4200` and expects the API at
`http://localhost:8080/api` (configured in `src/environments/environment.ts`).

**Alternative: Docker.** Instead of running `npm start` and `mvn
spring-boot:run` in separate terminals, `docker compose up --build` from
the repo root builds and runs this frontend (via Nginx) alongside the
backend and a real PostgreSQL database — see root `README.md` → "Docker".
`environment.prod.ts` already uses a relative `/api` path specifically so
Nginx's reverse proxy setup (`nginx.conf`) works without any URL changes.

## Sample logins (from the backend's dev seed data)

| Email | Role | Password |
|---|---|---|
| admin@eems.local | SUPER_ADMIN | ChangeMe123! |
| manager@eems.local | MANAGER | ChangeMe123! |
| employee@eems.local | EMPLOYEE | ChangeMe123! |

Try logging in as each to see how the dashboard, employee list, and leave
actions change per role — e.g. `employee@eems.local` sees only their own
record and cannot approve leave; `manager@eems.local` sees direct reports
and can approve/reject; `admin@eems.local` sees everything plus the
Departments tab.

## Vertical sidebar navigation

The shell (`shared/layout/shell.component`) moved from a single crowded
horizontal top bar (11 nav items in one row, once every feature this
conversation built got a link) to a **vertical left sidebar**, with a
slim top bar left for just the account menu. Each nav item has an
icon; a collapse toggle at the sidebar's bottom switches to an
icons-only mode (persisted in `localStorage`, so it doesn't reset on
reload) for a denser view or a narrower window. Below 900px the sidebar
auto-collapses to icons-only regardless of the stored preference, so
it doesn't eat the whole screen on a small viewport.

## Architecture notes

- **Login screen** is a split-panel layout: a branding/highlights panel
  (auto-rotating feature callouts, a decorative preview chart reusing
  `app-chart` in a new light-text mode for dark backgrounds) alongside
  the actual sign-in form (password show/hide toggle, time-of-day
  greeting). Collapses to just the form below 900px viewport width — the
  hero panel is decorative, not essential, so it's the first thing to go
  on small screens rather than squeezing both panels into a phone width.
- **Standalone components** throughout (no NgModules) — Angular's current
  recommended approach.
- **`core/`** holds cross-cutting concerns: auth service (token storage via
  `localStorage`, exposed as a signal), HTTP interceptor (attaches the
  bearer token, logs out on 401), and two guards (`authGuard` for
  "must be logged in", `roleGuard(...)` for route-level RBAC that mirrors
  the backend's `SecurityConfig`).
- **`features/`** holds one folder per screen — login, dashboard,
  employees (list + form, shared between create/edit), departments,
  positions, leave, change-password, analytics.
- **`shared/layout/`** holds the authenticated shell (top nav with logo,
  account menu, and footer), wrapping all routes except `/login`.
- **`shared/chart/`** — a thin wrapper around Chart.js (`app-chart`) used
  by the login page, dashboard, and analytics page. `ng2-charts` was the
  more obvious choice but its current release requires Angular 21+; this
  project targets Angular 18, so a small custom wrapper avoided the
  version conflict entirely. Every chart in the app passes a
  `description` input — a one-line caption below the canvas explaining
  what it shows and how to read it (e.g. that "Headcount by Department"
  only counts ACTIVE employees, or that a doughnut's slice size is a
  share, not an absolute count). This isn't decorative — a chart with no
  axis context is genuinely hard to interpret correctly without it. The
  one exception with an honest caption instead of a real one: the login
  page's chart is decorative/example data, and its description says so
  rather than implying it's live.

  **A serious, real bug, fixed as of this version: `[labels]`/`[data]`
  bound to plain component getters instead of signals can freeze or
  crash the browser tab.** This hit both the Dashboard and Analytics
  pages — every chart-feeding property on both was a plain TypeScript
  getter (`get departmentLabels(): string[] { return ...map(...); }`),
  each building a brand-new array on every single Angular
  change-detection check, even when nothing had actually changed.
  `ChartComponent` compares its `labels`/`data` inputs by reference, so
  a new array every check meant `ngOnChanges` fired every check, which
  destroys and rebuilds the underlying Chart.js instance, which
  schedules a `requestAnimationFrame` that Angular's zone intercepts
  and triggers *another* change-detection cycle from - a genuine
  infinite render loop, not just "a bit slow." This is almost certainly
  what caused reports of the browser hanging or a "this page is
  slowing down your browser" warning when visiting either page.
  **Fixed** by converting every chart-feeding property on both pages
  from a `get x()` getter to a `readonly x = computed(() => ...)`
  signal — `computed()` caches its result and only recomputes when a
  signal it actually reads changes, so the same underlying data now
  yields the same array reference across checks, and the loop can't
  start. **Any new chart added to this app must feed it from a
  `computed()` signal, never a plain getter** — this is a real,
  demonstrated way to freeze the whole tab, not a style preference.

- Route guards are a UX convenience only. The backend re-checks every
  request and is the actual security boundary — a hidden nav link is not
  access control on its own.

## Change password (SMS-confirmed)

Available from the account menu (click your email in the top nav) →
"Change password". Two-step UI:

1. Enter current password + new password (and a phone number, if you
   haven't set one yet — optional otherwise). Submitting triggers a
   6-digit code sent to that phone.
2. Enter the code to confirm. The password only actually changes at
   this step.

**Note:** no real SMS provider is wired up on the backend — the OTP
prints to the backend's console instead of sending an actual text (see
`eems-backend/README.md` → "Change password" section for how to wire up
a real provider). All seeded dev accounts already have placeholder phone
numbers, so you can try the full flow immediately without needing to set
one first.

## HR Analytics dashboard

Visible in the nav (and as a "Full analytics" link from the main
dashboard) to SUPER_ADMIN, HR_ADMIN, and AUDITOR roles — matches the
backend's `/api/reports/**` access rule. Shows headcount by department
(bar) and by status (doughnut), leave requests by status and by type,
plus summary stat cards (active headcount, average tenure, new hires,
attrition rate, pending approvals). "Export Excel" and "Export PDF"
buttons download the same data as a formatted file from the backend.

A **Department filter** dropdown next to the export buttons narrows
the "Headcount by Department" chart and a new **data table** below the
charts (Department / Active Headcount / Share of Total) down to one
department, or "All departments" to show everything — entirely
client-side, since the backend already returns every department's
headcount in a single response; filtering here just changes what's
displayed, not what's fetched. It's deliberately scoped to only the
department-granular data — `headcountByStatus`/`leaveRequestsByStatus`/
`leaveRequestsByType` aren't broken down per department in the
backend's response shape, so the filter honestly doesn't touch those
charts rather than pretending to.

At the bottom of the page, a **"Connect Power BI"** card lists the three
`/api/powerbi/*` endpoint URLs with copy buttons, plus a "Copy access
token" button — paste both into Power BI Desktop's "Get Data → Web"
credential dialog as an `Authorization: Bearer <token>` header for quick
ad-hoc testing. This is genuinely just a convenience for the manual
Desktop flow — no real Power BI Embedded integration exists here (that
needs Azure AD app registration and Power BI tenant credentials); see
`eems-backend/README.md` → "Power BI integration" for the full picture,
including the direct-PostgreSQL-connection path that's the right choice
for production/scheduled refresh.

The main **Dashboard** is a two-column layout for
SUPER_ADMIN/HR_ADMIN/AUDITOR: quick stats and quick-link buttons on the
left, a right-side sidebar with the attrition-rate stat and a live
"Headcount by Department" chart (collapses to a single column below
900px, and to just the left column entirely for roles without analytics
access — there's nothing to put in a sidebar for them). It also has
several genuinely interactive widgets, not just static counters:

- **Auto-refresh** — data reloads every 30 seconds automatically, with
  a manual refresh button and a "Updated Xs ago" label (ticking every
  second) so it's clear the numbers aren't stale.
- **Clickable stat cards** — "Employees visible to you" and "Your leave
  requests" navigate to the relevant page on click, with a hover lift
  for affordance.
- **Expandable Pending Approvals** (SUPER_ADMIN/HR_ADMIN/MANAGER) —
  clicking the card expands it inline into the actual list of pending
  leave requests, each with working Approve/Reject buttons right there
  (reuses the same `LeaveApiService.decide` call as the full Leave
  page) — turns a number into something actionable without leaving the
  dashboard.
- **Quick "Request leave"** — a collapsible inline form (any
  authenticated user) that submits via the same API as the Leave page,
  so you can request time off without navigating away.
- **Time-of-day greeting** ("Good morning/afternoon/evening") replacing
  the plain "Signed in as" header.

## Employee bulk import + export

On the Employees page, HR_ADMIN/SUPER_ADMIN see "Import CSV/Excel",
"Download template", and now **"Export CSV"** buttons (Employees didn't
have export at all until this version — Departments and Positions both
already had it, Employees was the one page missing it). "Import
CSV/Excel" accepts a starter `.csv` with two example
rows — one leaves email/password blank to demonstrate the auto-generated
credentials feature. Pick a `.csv` or `.xlsx` file (see
`eems-backend/README.md` → "Employee bulk import + export" for the
expected column layout) and the page shows a per-row success/failure summary
after upload, then refreshes the employee list. A snack-bar also
confirms the outcome immediately ("Imported successfully — N
employee(s) created" / "Import finished with issues — N of M rows
created"), on top of the detailed summary card — the two aren't
redundant: the toast is the unmissable first signal, the card is where
you go to see exactly which rows failed and why.

**A real bug, fixed as of this version, identical across the Employees,
Departments, and Positions import buttons**: if the import *request
itself* failed (network error, a 403, a 500 — anything before the
backend even got to process individual rows), the component silently
did nothing — the spinner just stopped with no success message, no
error message, no visible change at all. That's what "nothing happens
after I import" looked like. All three now surface the actual backend
error message in a card, the same way every other action in this app
already did.

The employee table itself is now paginated client-side (10/25/50/100 per
page) — there's no server-side paging endpoint for employees, so the
full list is fetched once and sliced locally via a `computed()` signal
rather than adding one just for this.

## User Management (admin role grant/revoke)

New `/admin/users` route, nav-visible only to SUPER_ADMIN (the "Users"
link). Table of every user with an inline role dropdown and an
enabled/disabled toggle — changing either calls the backend
immediately (no separate "Save" step). Your own row shows a "you" chip
and has both controls disabled — matches the backend's
self-modification guard, so the UI doesn't even offer an action the API
would reject; if you somehow trigger it anyway (e.g. two tabs open), the
error card at the top surfaces the backend's actual rejection message
rather than failing silently.

## Audit Log viewer

New `/audit-logs` route, nav-visible to SUPER_ADMIN and AUDITOR ("Audit
Log" link) — server-side paginated table (`MatPaginator`, not the
client-side slicing used for Employees, since this can genuinely grow
large) with filter fields for entity type and actor email.

## Delete for Departments and Positions

Both list pages now have a delete icon per row. Confirms via a native
`confirm()` dialog first (not a custom modal — this is genuinely
destructive and rare enough that a browser-native confirm is the right
weight), then calls the backend. If the backend refuses (still
referenced by employees, etc.), the actual rejection message from the
API shows in an error card rather than a generic failure.

## Resend code (change-password)

The verify step now has a "Resend code" button alongside "Start over" —
re-submits the same current/new password already entered in step 1
(the form isn't reset when moving to the verify step) rather than
making you type everything again. A 30-second client-side cooldown
prevents spamming it; the backend's own rate limit (5/hour) is the real
backstop if that's ever bypassed.

## Login page: header, footer, Caps Lock warning + shake feedback

The login page now has a full-width header and footer wrapping the
split-panel layout, not just the two panels alone:

- **Header** — logo/brand, a live clock (updates every second), and a
  theme accent picker (4 color swatches that change the hero panel's
  gradient, persisted to `localStorage`). This is deliberately **not** a
  full dark-mode toggle — properly re-theming Angular Material's form
  fields for dark mode is a bigger job than a header widget should
  attempt, and a half-dark form with unreadable text would be worse than
  not offering it. The swatches only touch the hero panel's CSS custom
  properties (`--gradient-start`/`--gradient-end`).
- **Footer** — copyright with the current year, three quick links
  (Privacy/Terms/Support) that open a `MatSnackBar` — these are honestly
  labeled as demo placeholders in the message shown, not real pages, and
  a status indicator ("All systems operational" with a pulsing green
  dot). **That status indicator is decorative** — there's no real
  uptime/health-check API behind it — its tooltip says as much
  ("demo indicator, not a live health check") so it doesn't read as
  something it isn't.
- Caps Lock indicator appears under the password field the moment it's
  detected (via `KeyboardEvent.getModifierState('CapsLock')` on both
  keydown and keyup, since relying on just one can miss the initial
  state), and the login card does a brief shake animation on a failed
  login attempt — physical feedback alongside the red error text, not
  instead of it.

## Employee search + Employee ID

The Employees page has a search box (name, email, or employee ID —
client-side, filtering the already-fetched list rather than a new
server-side search endpoint) and a new "Employee ID" column showing
each employee's generated `employeeCode` (e.g. `EMP-0007`). Pagination
and the "no results" empty state both respect the filtered count, not
the full list — searching resets you to page 1 rather than leaving you
stranded on a now-out-of-range page.

An **"Employee ID Format"** card on the User Management page
(SUPER_ADMIN/HR_ADMIN) lets you set the prefix/suffix pattern and shows
a live example of the next code that would be generated. Saving only
affects employees created afterward — existing employee IDs are never
renamed.

## Company Branding (logo upload)

A "Company Branding" card at the top of the User Management page
(SUPER_ADMIN only) — upload or revert the company logo, with a live
preview. The logo itself is fetched via a new `BrandingService`
(`core/services/branding.service.ts`), which loads it once and caches
it as a signal, falling back silently to the default `logo.svg` asset
if nothing custom is set or the fetch fails for any reason — a broken
logo should never be the thing that makes the app unusable. Used in
three places: the sidebar header, the footer, and the login page (the
one place in the app that fetches data with no auth token at all,
since the backend's GET endpoints for this are deliberately public —
see `eems-backend/README.md` → "Company logo").

## Employee Profile page

`/employees/:id` route — a "View profile" (eye icon) link sits next to
each row's Edit icon on the Employees list. Shows a profile photo (a
circular avatar in the page header, falling back to colored initials
when none is set) with an upload/change button and a "Remove photo"
link when one exists, plus core employment info (position, department,
manager, status, hire/exit date), plus real inline management for
several of the normalized-schema tables:
- **Address** — view/edit inline (self, direct manager, or HR/Admin/Auditor).
- **Emergency contacts** — list, add, delete.
- **Documents** — **real file upload** is the primary path now: pick a
  document type + a file, and the actual bytes are sent and stored
  server-side (see `eems-backend/README.md` → "Real file
  upload/download"). A secondary "link to a file stored elsewhere
  instead" toggle keeps the old metadata-only path available for
  documents that genuinely live externally. Uploaded documents show a
  Download button; metadata-only ones show their link instead — the UI
  only offers Download where there's actually something to download.
- **Offer letter** — a button in the page header generates and
  downloads a PDF offer letter from this employee's current position,
  department, and most recent salary record (see `eems-backend/README.md`
  → "Offer letter generation" for what it does and doesn't include).

**Not yet built on the frontend**: **salary** and **employment
contracts** still have full backend CRUD (see `eems-backend/README.md`
→ "Employee database extension") but no UI on this profile page —
reachable via direct API calls only. Adding them would follow the same
pattern as the sections above.

## Manageable employee list (Create/Edit)

Employees previously had no create/edit UI at all — only import and a
read-only table. Now:
- **"New Employee"** button (HR_ADMIN/SUPER_ADMIN) → `/employees/new`.
- **Edit icon** per row (HR_ADMIN/SUPER_ADMIN/MANAGER, backend still
  enforces manager-scoped visibility — a visible Edit button isn't the
  security boundary, the API's RBAC is) → `/employees/:id/edit`.

Both routes share one component (`EmployeeFormComponent`), just with
different fields visible/enabled (email, hire date, and initial
password are create-only; status is edit-only). Position, Department,
and Manager are all dropdowns fed by their respective APIs — Manager's
options come from the already-loaded employee list, filtered to exclude
the employee being edited (so you can't set someone as their own
manager from the UI, though the backend doesn't separately guard against
this either — a genuine gap worth closing if this goes further).

## Auto-generated login credentials

Email and initial password on the Create Employee form are both now
**optional**, with a placeholder hint ("Auto-generated if left blank")
rather than a required field defaulting to a fixed password. Leave both
blank and the backend generates a unique login email and a secure
random temporary password.

When either was generated, the form doesn't navigate away after
submit — it shows a **"Employee created"** panel with the generated
email/password, copy buttons for each, and a warning that this is the
only time they'll be shown. Clicking "Done" is what actually navigates
to the employee list; this is deliberate, not a bug, since the
plaintext temporary password can't be retrieved again once you leave.

Same pattern on the **User Management** page (`/admin/users`) — each
row has a "Reset password" button (disabled for your own account, same
reasoning as the role/enabled controls) that generates a fresh temporary
password and shows it in an identical one-time reveal panel. Rows with
a pending temporary password show a small clock icon next to the email
as a hint that this account still needs to change it.

**Enforcement on login**: if the backend flags an account as needing a
password change, login redirects straight to `/change-password` (with
a banner explaining why) instead of `/dashboard`. This is a **redirect
only** — there's no route guard stopping someone from navigating
elsewhere afterward by changing the URL. See
`eems-backend/README.md` → "Admin-generated temporary credentials" for
the same caveat on the backend side.

## Leave balance calculator + management

The **Leave** page now shows a "My Leave Balances" card above the
request form — one row per tracked type (ANNUAL/SICK/PARENTAL) with a
progress bar (used + pending vs. allocated) and an "X pending approval"
note when applicable. UNPAID and OTHER don't appear here since they're
not balance-tracked (see `eems-backend/README.md` → "Leave balance
calculator + management").

Submitting a request that would exceed the available balance now
surfaces the backend's actual error message inline on the form — this
also fixed a real gap: the submit handler previously had **no error
callback at all**, so any submission failure (this new validation, or
anything else) would have failed completely silently.

New **`/leave-balances`** page (HR_ADMIN/SUPER_ADMIN, nav-visible as
"Leave Balances") — a table of every set allocation for the current
year (allocated/used/pending/available, with available shown in red if
negative — i.e. more was approved than allocated), a form to set
or update one employee's allocation for a tracked type/year, and two
expandable panels (collapsed by default, since they're less frequently
used than the single-employee form above them):
- **Bulk allocate** — set the same allocation for every active
  employee at once, optionally scoped to one department.
- **Carry over between years** — roll each employee's unused balance
  from one year into the next, with an optional cap. Both show a
  snack-bar confirming how many employees were affected, or the
  backend's actual rejection message on failure (e.g. trying to set an
  allocation for UNPAID/OTHER, which the backend correctly refuses).

## Positions page

`/positions` route (HR_ADMIN/SUPER_ADMIN, nav-visible) — list + a
create/edit form (same form, toggling mode), plus **"Import CSV"**,
**"Export CSV"**, and **"Download template"** buttons (columns: `title,
grade, salaryBand, departmentName`) — import added specifically because
seeding dozens of positions one at a time through the create form isn't
practical. Positions replace the old free-text job title on employees;
create these before assigning them via the employee Create/Edit form or
CSV import's `positionTitle` column. Each row has an expand toggle
(shows the job description inline), an Edit icon (opens the form
pre-filled — `title` is read-only while editing, since it's what CSV
import and other lookups match against), and a delete icon (see "Delete
for Departments and Positions" above). The create/edit form now
includes a **job description** textarea — surfaced on both this page
and any job posting linked to that position.

## Job Postings

New `/job-postings` route, nav-visible to everyone (not role-gated at
the route level — the backend scopes what each role actually sees, so
there's nothing to hide in the UI). Card-based layout, not a table —
descriptions are prose, which doesn't fit table cells well.

- **Everyone** sees an expandable description per posting.
- **HR_ADMIN/SUPER_ADMIN** additionally get a "New posting" button and
  Edit/Delete on every card, and see every posting regardless of status
  or visibility (drafts, closed, external-only included).
- **Everyone else** only sees what the backend's visibility scoping
  returns: `OPEN` postings marked `INTERNAL` or `BOTH` — the same
  filtering happens server-side too, so this isn't just a UI-level
  hide (see `eems-backend/README.md` → "Job postings").
- `EXTERNAL` visibility is a label for HR's own tracking of where a
  role is being advertised elsewhere — this app has no public careers
  page that would actually publish it anywhere, and the UI doesn't
  pretend otherwise.

## Org Chart

New `/org-chart` route, nav-visible to everyone — built entirely from
`GET /api/employees` (no new backend endpoint needed) by turning the
flat, already-visibility-scoped employee list into a tree using each
employee's existing `managerId`. A person whose manager isn't present
in *their own* result set (e.g. a MANAGER only ever sees their direct
reports, not their own manager) is shown as a root rather than
dropped — the chart stays complete for whatever data the viewer
actually has access to, it just starts one level lower for some roles.
Each node shows name (links to their profile), position title,
department, and a direct-report count, with a collapse/expand toggle
per node.

**A note on why this shows position titles (CEO, Director, Team
Leader, etc.) rather than app roles** (`SUPER_ADMIN`/`HR_ADMIN`/
`MANAGER`/`EMPLOYEE`/`AUDITOR`): those are two genuinely different
things in this app. **Role** controls what someone can *do* in the
system (approve leave, manage users, view salaries). **Position**
describes where they sit in the org chart. A Director doesn't
automatically get HR-admin permissions just from their title, and a
Team Leader isn't automatically an app-level MANAGER — see
`eems-backend/README.md` for the full reasoning. The 7 requested
titles (CEO, DCEO, Managing Director, Director, Manager, Team Leader,
Project Manager) are meant to be created as **Positions** (see the
generated `org-titles-positions.csv`), not new Role values.

## Department bulk import + export

Departments page has "Import CSV", "Export CSV", and "Download
template" buttons matching the Employees/Positions pages' pattern — see
`eems-backend/README.md` → "Department bulk import + export" for the
expected columns (`name, location, parentDepartmentName`) and what the
export contains. Each row also has a delete icon (see "Delete for
Departments and Positions" above).

## Not yet implemented (natural next steps)

- Real-time dashboard updates (FR-6.2/6.5) — currently a plain REST fetch
  on page load (with 30-second polling, added since — see the Dashboard
  section above); would need a WebSocket/SSE service and a shared
  connection in `core/services` for genuinely instant updates.
- Performance review screens (SRS section 3.5).
- GDPR self-service screens: consent management, "download my data" (DSAR),
  erasure request (SRS section 3.7) — the backend doesn't expose these
  endpoints yet either.
- Attendance clock-in/out UI (SRS section 3.4, beyond leave).
- Server-side pagination on Departments/Positions/Leave tables (Employees
  now has client-side pagination — see "Employee bulk import" above;
  the others are small enough lists that it hasn't been needed yet).
- Silent token refresh in the HTTP interceptor (currently just logs the
  user out on a 401 rather than retrying with a refreshed token).
- A way to view/edit your current phone number outside the change-password
  flow (currently there's no "my profile" screen — the phone field there
  is the only place to set or update it).
- Historical trend charts on the analytics page (current charts show a
  point-in-time snapshot only, matching what the backend currently computes).
- Salary and employment contract UI on the Employee Profile page — the
  backend has full CRUD for both (see `eems-backend/README.md` →
  "Employee database extension"), only the frontend hasn't caught up yet.
- Attendance, performance reviews, and training — see
  `eems-backend/README.md` → "What's implemented" for why each is
  deliberately deferred rather than rushed.
