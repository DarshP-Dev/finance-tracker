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
