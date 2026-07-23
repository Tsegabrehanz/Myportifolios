-- Power BI reporting views for EEMS (PostgreSQL / prod profile only).
--
-- Run this against your prod database after the application has created
-- its tables (Hibernate ddl-auto is "validate" in prod - see
-- application.yml - so the app tables must already exist before this
-- script runs).
--
-- Why views instead of pointing Power BI straight at the app's tables:
--   - Flattens joins (department name, manager name) so Power BI's model
--     doesn't need to replicate that logic in DAX.
--   - Excludes sensitive columns (password_hash, national_id, phone_number)
--     entirely, rather than relying on report authors to remember not to
--     drag them into a visual.
--   - Gives you a stable contract: internal schema refactors don't have
--     to break every Power BI report, as long as the view keeps its shape.
--
-- Connect Power BI Desktop: Get Data -> PostgreSQL database -> host/port/
-- database name -> pick "eems_reporting_role" credentials (see below) ->
-- select these views under the public schema.

-- ---------------------------------------------------------------------
-- 1. Read-only role for Power BI's connection
-- ---------------------------------------------------------------------
-- Use a dedicated role rather than the application's own DB user, so
-- Power BI's connection can never write, and so you can rotate/revoke
-- its password independently of the app's credentials.

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'eems_reporting_role') THEN
        CREATE ROLE eems_reporting_role LOGIN PASSWORD 'CHANGE_ME_BEFORE_USE';
    END IF;
END
$$;

GRANT CONNECT ON DATABASE eemsdb TO eems_reporting_role;
GRANT USAGE ON SCHEMA public TO eems_reporting_role;

-- ---------------------------------------------------------------------
-- 2. Reporting views
-- ---------------------------------------------------------------------

CREATE OR REPLACE VIEW vw_employee_summary AS
SELECT
    e.id                    AS employee_id,
    e.first_name,
    e.last_name,
    p.id                    AS position_id,
    p.title                 AS position_title,
    e.status,
    e.hire_date,
    e.exit_date,
    d.id                    AS department_id,
    d.name                  AS department_name,
    d.location              AS department_location,
    m.id                    AS manager_id,
    (m.first_name || ' ' || m.last_name) AS manager_name
FROM employee e
LEFT JOIN department d ON d.id = e.department_id
LEFT JOIN position p   ON p.id = e.position_id
LEFT JOIN employee m   ON m.id = e.manager_id;

CREATE OR REPLACE VIEW vw_department_headcount AS
SELECT
    d.id                    AS department_id,
    d.name                  AS department_name,
    d.location              AS department_location,
    COUNT(e.id) FILTER (WHERE e.status = 'ACTIVE') AS active_headcount,
    COUNT(e.id)             AS total_headcount
FROM department d
LEFT JOIN employee e ON e.department_id = d.id
GROUP BY d.id, d.name, d.location;

CREATE OR REPLACE VIEW vw_leave_summary AS
SELECT
    l.id                    AS leave_request_id,
    l.type                  AS leave_type,
    l.status                AS leave_status,
    l.start_date,
    l.end_date,
    (l.end_date - l.start_date + 1) AS duration_days,
    l.created_at,
    l.decided_at,
    e.id                    AS employee_id,
    (e.first_name || ' ' || e.last_name) AS employee_name,
    d.id                    AS department_id,
    d.name                  AS department_name,
    approver.id             AS approved_by_id,
    (approver.first_name || ' ' || approver.last_name) AS approved_by_name
FROM leave_request l
JOIN employee e         ON e.id = l.employee_id
LEFT JOIN department d  ON d.id = e.department_id
LEFT JOIN employee approver ON approver.id = l.approved_by;

CREATE OR REPLACE VIEW vw_leave_balance AS
SELECT
    lb.id                   AS balance_id,
    lb.balance_year         AS year,
    lb.leave_type,
    lb.allocated_days,
    lb.used_days,
    (lb.allocated_days - lb.used_days) AS remaining_after_used, -- doesn't account for pending requests - see backend README
    e.id                    AS employee_id,
    (e.first_name || ' ' || e.last_name) AS employee_name,
    d.id                    AS department_id,
    d.name                  AS department_name
FROM leave_balance lb
JOIN employee e         ON e.id = lb.employee_id
LEFT JOIN department d ON d.id = e.department_id;

-- ---------------------------------------------------------------------
-- 3. Grant read access to the views only (not the underlying tables)
-- ---------------------------------------------------------------------
GRANT SELECT ON vw_employee_summary, vw_department_headcount, vw_leave_summary, vw_leave_balance TO eems_reporting_role;

-- Re-run this GRANT after creating/replacing views in the future, or set
-- a default privilege so new views are automatically readable:
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO eems_reporting_role;
