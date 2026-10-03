# finance-tracker

## Run locally

Start PostgreSQL, then run the backend in a PowerShell terminal:

```powershell
cd backend/backend
.\start-dev.ps1
```

The first run prompts for the local PostgreSQL password and stores it with Windows
user-bound encryption in the Git-ignored `.local` directory. The script also creates
a random development JWT secret and reuses it across restarts. Existing `DB_PASSWORD`
and `JWT_SECRET` environment variables take precedence.

The backend reads deployment configuration from environment variables:

- `DB_URL` (defaults to the local `finance_tracker_db` database)
- `DB_USERNAME` (defaults to `postgres` for local development)
- `DB_PASSWORD` (required)
- `JWT_SECRET` (required when the development script is not used)
- `CORS_ALLOWED_ORIGINS` (defaults to `http://localhost:3000`)
- `DB_DDL_AUTO`, `JPA_SHOW_SQL`, and `JPA_FORMAT_SQL` (optional)

Use separately managed secrets in production and set `DB_DDL_AUTO=validate` after
introducing database migrations.

In a second terminal, start the frontend:

```powershell
cd frontend/finance-tracker-frontend
npm run dev
```

Open http://localhost:3000. Keep both terminals running: the dashboard needs the
backend on port 8080. If the backend was stopped, start it and click **Retry** on
the dashboard. Sign in again if your previous session has expired.

## Recurring transactions

Authenticated requests to `/api/recurring-transactions` can create, list, read,
update, and delete recurring definitions. `PATCH /{id}/pause` and `PATCH /{id}/resume`
control whether a definition can generate. `POST /{id}/generate` creates exactly one
ordinary transaction and returns it for debugging.

`startDate` is the first occurrence: creating a definition also records one ordinary
transaction on that date and advances `nextOccurrence`. Automatic processing and later manual
generation use the current
`nextOccurrence` as the transaction date, then advances it. Monthly and yearly dates
stay anchored to the original day (January 31 goes to February 28 and then March 31).
Generation automatically deactivates a definition when the following occurrence
would be after `endDate`. Resuming does not backfill missed dates; manual generate
calls advance one occurrence at a time. Editing the schedule moves the next
occurrence past the most recently generated date so that date is never reused.

The backend checks active, due definitions daily at 3:00 a.m. in the configured zone
and catches up missed occurrences from `nextOccurrence`. Each definition is locked and
processed in its own database transaction. A failed definition does not stop the rest.
At most 100 occurrences per definition are generated per run by default; any remaining
due dates stay queued for the next run. Configure local or continuously running servers
with `RECURRING_SCHEDULER_ENABLED`, `RECURRING_SCHEDULER_CRON` (Spring's six-field cron),
`RECURRING_SCHEDULER_ZONE` (default `America/Toronto`), and
`RECURRING_SCHEDULER_MAX_CATCH_UP` (default `100`).

Vercel containers can stop between requests, so the in-process timer alone is not a
reliable production trigger. `vercel.json` configures a daily request at 08:00 UTC to
`/api/internal/recurring/process-due`. Set a long random `CRON_SECRET` in Vercel; the
endpoint refuses requests without its matching `Authorization: Bearer` header. Set
`RECURRING_SCHEDULER_ENABLED=false` on Vercel to use only that external trigger.
Vercel runs configured cron jobs only on production deployments. Verify the cron appears
in the Vercel dashboard after deployment and that its first invocation succeeds.

### Upcoming recurring transactions

The Transactions page has an **Upcoming** tab with period filters and projected
income, expenses, and net cash flow. The Dashboard shows the next five projected
occurrences, with a link to the full Upcoming view. These amounts cover known active
recurring schedules only; they are not an account balance or recorded transactions.

Authenticated `GET /api/recurring-transactions/upcoming` returns individual projected
occurrences, and `GET /api/recurring-transactions/forecast` returns period totals and
counts. Both accept optional ISO `from` and `to` dates. The default is today through
30 days later, inclusive. Ranges must be ordered and no longer than 12 months.
Projection begins at each schedule's `nextOccurrence`, uses the same calendar rules
as actual generation, includes occurrences on `endDate`, and excludes paused schedules.
These GET endpoints never generate transactions or update recurring definitions.

## Deploy with Vercel and Neon

The repository is configured as one Vercel Services project. Vercel serves the
Next.js application from `frontend/finance-tracker-frontend` and runs the Spring
Boot API from `backend/backend` as a Java 21 container. Requests under `/api/*`
are routed to Spring Boot; all other requests are routed to Next.js.

The production database should be a Neon PostgreSQL database using Neon's pooled
endpoint. Configure these variables in Vercel without committing their values:

- `DB_URL`: pooled JDBC URL in the form
  `jdbc:postgresql://<neon-pooled-host>/<database>?sslmode=require&channelBinding=require`
- `DB_USERNAME`: Neon database role
- `DB_PASSWORD`: Neon database password
- `JWT_SECRET`: stable random JWT signing secret of at least 32 bytes
- `DB_DDL_AUTO`: use `update` for the initial beta deployment; move to `validate`
  after adding managed database migrations

Vercel supplies `PORT`; the backend continues to use port 8080 when `PORT` is not
set. The frontend uses same-origin `/api/...` requests in production and continues
to use `http://localhost:8080` during local development. Set
`NEXT_PUBLIC_API_BASE_URL` only when an explicit API origin override is needed.

The datasource pool defaults are conservative for beta traffic and can be
overridden through Spring's standard environment variables:

- `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` (default `5`)
- `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE` (default `0`)
- `SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT` (default `30000` ms)
- `SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT` (default `60000` ms)
- `SPRING_DATASOURCE_HIKARI_MAX_LIFETIME` (default `600000` ms)

`CORS_ALLOWED_ORIGINS` continues to default to `http://localhost:3000` for local
development. Production and preview deployments use the shared Vercel origin.
