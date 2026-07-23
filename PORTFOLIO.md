<table border="0">
<tr>
<td width="65%" valign="top">

# Welcome to my Enterprise Employee Management System (EEMS) showcase.

This repository is a full-stack, containerized HR platform built from
scratch — employee lifecycle management, leave requests with real
balance enforcement (not just a display), HR analytics with PDF/Excel
export, a Power BI data feed, role-based access control down to the
individual record, and Docker containerization end to end.

Every screen below is from the actual running application, not a
mockup.

</td>
<td width="35%" valign="top" align="center">

<a href="https://github.com/<your-github-username>/<your-repo-name>">
<img src="https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png" width="70" alt="GitHub" />
</a>

**@<your-github-username>**

[View source](https://github.com/<your-github-username>/<your-repo-name>) ·
[Architecture](./ARCHITECTURE.md) ·
[Backend README](./eems-backend/README.md) ·
[Frontend README](./eems-frontend/README.md)

</td>
</tr>
</table>

---

## Gallery

<table border="0">
<tr>
<td width="50%">
<img src="docs/screenshots/01-login.png" alt="Login page" width="100%" />
<p align="center"><sub>Split-panel login — live clock, theme accent picker, Caps Lock warning</sub></p>
</td>
<td width="50%">
<img src="docs/screenshots/02-dashboard.png" alt="Dashboard" width="100%" />
<p align="center"><sub>Role-scoped dashboard — auto-refreshing, expandable approvals, quick leave request</sub></p>
</td>
</tr>
<tr>
<td width="50%">
<img src="docs/screenshots/03-analytics.png" alt="HR Analytics" width="100%" />
<p align="center"><sub>HR Analytics — headcount, attrition, tenure, leave utilization, PDF/Excel export</sub></p>
</td>
<td width="50%">
<img src="docs/screenshots/04-employee-profile.png" alt="Employee profile" width="100%" />
<p align="center"><sub>Employee profile — core info, address, emergency contacts, documents</sub></p>
</td>
</tr>
<tr>
<td width="50%">
<img src="docs/screenshots/05-leave-balances.png" alt="Leave balance management" width="100%" />
<p align="center"><sub>Leave balance calculator — allocate, bulk-allocate, carry over between years</sub></p>
</td>
<td width="50%">
<img src="docs/screenshots/06-audit-log.png" alt="Audit log" width="100%" />
<p align="center"><sub>Paginated, filterable audit trail — every create/update/delete/decision</sub></p>
</td>
</tr>
</table>

---

## What's under the hood

| | |
|---|---|
| **Backend** | Spring Boot 3.3, Spring Security + JWT, JPA/Hibernate, PostgreSQL/H2 |
| **Frontend** | Angular 18 (standalone components), Angular Material, Chart.js |
| **Database** | 16 tables — see [`ARCHITECTURE.md`](./ARCHITECTURE.md) for the full schema and ER diagram |
| **Containerization** | Docker + Docker Compose — Postgres, Spring Boot, Nginx-served Angular, all networked |
| **Security** | Role-based access control enforced at both the URL and service layer, JWT auth, SMS-confirmed password changes, rate limiting |
| **Extras** | Power BI data feed (SQL views + REST endpoints), HR analytics with report export, audit logging |

See [`ARCHITECTURE.md`](./ARCHITECTURE.md) for a full folder-by-folder
explanation, the database schema, and exactly how the pieces
communicate — and each sub-project's README for a feature-by-feature
breakdown of what's implemented.
