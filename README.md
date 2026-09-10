# finance-tracker

## Run locally

Start PostgreSQL, then run the backend in a PowerShell terminal:

```powershell
cd backend/backend
.\start-dev.ps1
```

The script creates a random development JWT secret in the Git-ignored
`.local/jwt-secret` file and reuses it across restarts. An existing `JWT_SECRET`
environment variable takes precedence. Use a separately managed secret in production.

In a second terminal, start the frontend:

```powershell
cd frontend/finance-tracker-frontend
npm run dev
```

Open http://localhost:3000. Keep both terminals running: the dashboard needs the
backend on port 8080. If the backend was stopped, start it and click **Retry** on
the dashboard. Sign in again if your previous session has expired.
