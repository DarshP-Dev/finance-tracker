# Authentication Phase 1: local integration and browser checks

Windows command examples use `C:\path\to\FinanceTracker`; substitute your checkout path.

This is a local-only checklist. Do not use a production URL, change Vercel, rerun
baseline, rerun V2, repair/clean history, or delete existing financial records.
Current Git changes remain uncommitted.

## Read-only setup verification, October 14, 2026

- Verified local PostgreSQL 17.9: 127.0.0.1:5432, finance_tracker_db.
- History already contains one successful BASELINE 1 and one successful SQL V2.
  V2's database timestamp is 2026-10-14 00:14:19, before this inspection.
- Strict Flyway history/checksum validation passed using the current packaged
  operator tool. No migrate/baseline command was executed.
- Full catalog comparison: all 147 immutable V1 signatures plus all 21 reviewed V2
  signatures match; no missing/changed/unexpected objects. V2 includes eight
  columns, seven validated constraints, five valid indexes and one table relation.
- All nine application table counts/full-row hashes and eight sequence states
  remained identical across this inspection. Captures are private and Git-ignored.
  The first capture was already after V2, so it is not a retrospective before/after
  V2 data audit. The reviewed V2 SQL contains no account/financial DML, existing
  table alteration or deletion; all original V1 schema objects remain intact.
- A fresh local custom-format pg_dump backup was created under ignored .local:
  auth-local-testing-20261014-012014.backup. Its archive listing is readable and
  contains all 11 table-data entries (9 existing tables, refresh sessions, history).
  This archive-readability check is not a new isolated restore rehearsal; no
  database restore was performed.
- Existing frontend and backend respond on localhost:3000/8080. CORS explicitly
  allows http://localhost:3000 with credentials. CSRF cookie is HttpOnly,
  Path=/api/auth, SameSite=Lax; Secure is false for local HTTP, as expected.
- The existing backend JVM started October 13 at 23:56:14, was launched through
  Maven spring-boot:run and includes DevTools. start-dev.ps1 defaults to local
  migrate if DB_MIGRATION_MODE is absent. V2's timestamp coincides with the earlier
  auth compilation period. A development restart could explain its application;
  available saved logs do not establish the exact initiating event or effective
  environment at that time.
- The old V1-only schema verifier rejects the new V2 objects by design. Use
  current migration-aware validation, not that adoption-only verifier.
- After the operator restarted with the private browser-testing launcher, the new
  backend PID was 40924. Listener inspection observed two established loopback
  PostgreSQL connections and zero non-loopback established connections. Both local
  servers responded 200; allowed-origin/credentials and CSRF cookie metadata
  passed again. The operator confirmed startup had no Flyway/Hibernate errors.

The user's permission to continue with read-only validation of the existing V2
state is recorded. Backend target verification and manual browser results are
recorded below as they occur; a listening localhost port alone does not prove the
backend's datasource is local.

## Test-count provenance

The published Flyway milestone ended with **515** executed tests. The historical
**555** workspace run included another **40** private operator-preparation cases:
13 Neon rehearsal, 13 production read-only preparation and 14 reconciliation
preparation. They remain intact under the Git-ignored private milestone archive.
An additional 15 unused role-fencing cases were also retained privately; they
were not part of the published 515 suite or the cited 555 run.

Authentication added **25** cases: 1 migration preservation, 16 real HTTP/session
integration, 7 JWT/cookie security and 1 remote startup migration safety test.
Thus **515 + 25 = 540**. The actual full-run log records 540 passes, zero failures,
errors or skips. No tracked test file or method was removed, no new @Disabled was
added, and Surefire/run-tests filtering configuration was unchanged.

Stale XML reports from those archived 55 private cases remain in target and can
inflate a naive XML sum to 595. Count only suites executed in the current run;
do not treat leftover reports as newly executed tests. During debugging, use the
existing guarded runner with focused authentication classes rather than rerunning
the entire suite unnecessarily.

## Temporary local backend configuration

Keep the already-running frontend. A backend restart is needed to establish its
local configuration and make expiry testing quick. In its PowerShell window,
press Ctrl+C, then:

```powershell
cd C:\path\to\FinanceTracker\backend\backend
.\.local\start-auth-browser-testing.ps1
```

The private, ignored launcher reuses start-dev.ps1 and Windows-encrypted local
password/JWT storage. It pins the datasource to local PostgreSQL and uses:

| Setting | Browser-testing value |
| --- | --- |
| Flyway startup | validate only |
| Hibernate | validate only |
| Access token TTL | PT60S |
| New refresh family TTL | PT1H |
| Refresh/CSRF cookies | HttpOnly, host-only, /api/auth, SameSite=Lax, Secure automatic |
| Allowed frontend | http://localhost:3000 |
| Recurring scheduler, external cron secret, AI and market providers | disabled |

No permanent property file is edited. Shell values are restored when the launcher
exits. Existing refresh families keep their original expiry; TTL changes affect
new login sessions. Cached investment prices can still display; live market/AI
requests are intentionally disabled during authentication checks.

If the frontend needs restarting later, its real command is:

```powershell
cd C:\path\to\FinanceTracker\frontend\finance-tracker-frontend
npm run dev
```

The frontend is Next.js/React, not Vite. Use http://localhost:3000 consistently,
with its development API target http://localhost:8080. Do not mix 127.0.0.1 and
localhost in browser URLs.

## Browser preparation

Open http://localhost:3000. Press F12 or Ctrl+Shift+I, select Network, enable Preserve
log, and filter for `api`. Reload. If no valid refresh cookie exists, the initial
POST /api/auth/refresh returning 401 is normal; the login form should appear.
Do not share Authorization headers, token/cookie values, login request payloads,
AuthResponse JSON or complete network exports.

## Tests, in order

1. **Existing account login.** If already restored, sign out first. Enter your
   existing LOCAL account credentials and sign in. Expect GET /api/auth/csrf 200,
   POST /api/auth/login 200 and the dashboard. Wrong-password testing belongs to
   step 9. Report statuses/UI outcome only.
2. **Reload persistence.** Refresh the page once. Expect brief session restoration,
   POST /api/auth/refresh 200 and the same signed-in account. In Application →
   Local Storage, financeTrackerToken/financeTrackerAuth should be absent.
   Under Cookies, inspect only metadata: financeTrackerRefresh is HttpOnly,
   /api/auth, SameSite=Lax, not Secure on HTTP; do not copy its value.
3. **Close/reopen tab.** Close this tab, keep the browser open, and open
   http://localhost:3000 in a new tab. Expect refresh 200 and restored login without
   entering a password. Closing the whole browser is a separate persistence policy;
   the refresh cookie has Max-Age but browser privacy settings can clear it.
4. **Automatic access renewal.** With Network logging, leave the page idle at least
   65 seconds after the latest login/refresh. Navigate to another section. Expect
   an initial financial 401/AUTHENTICATION_REQUIRED, one refresh 200 and successful
   retried requests. Stay signed in. No repeated refresh loop or 403-triggered
   logout should appear. Background requests may refresh first, so inspect the
   latest Network timing if no new 401 appears during your click.
5. **Cross-tab/logout.** Open two localhost tabs, both signed in. Click Sign out in
   one. Expect logout 204, login UI in both, refresh cookie removed, and no restored
   authentication after reload. A subsequent refresh with a valid CSRF request
   returns 401. Backend session revocation can be checked read-only using aggregate
   session state; do not expose token hashes or copy a revoked cookie. Existing
   access JWTs remain valid until expiry; logout does not instantly denylist them.
6. **New account registration.** Sign out, select Register, and use a distinct
   local test username/email (example.invalid is sufficient; no email is sent).
   Choose your own password and do not send it in chat. Expect register 201 and the
   existing instruction to sign in, then login 200. Do not delete an existing account.
7. **Financial features.** Use the newly created account for disposable examples.
   Check Dashboard, Transactions, Budgets, Investments, Analytics, recurring views
   and Notifications. Existing account views should remain accessible. If testing
   CRUD, create clearly labeled NEW test records; edit/delete only those newly
   created records, never existing finances. After access expiry, submit one small
   test transaction and confirm exactly one record is created after renewal. API
   failures must not silently repeat a financial mutation.
8. **Refresh expiry.** This needs a separate short-refresh session. Sign out, stop
   the backend and restart with:

   ```powershell
   .\.local\start-auth-browser-testing.ps1 -AccessTokenLifetime PT30S -RefreshTokenLifetime PT2M
   ```

   Log in again to create a NEW two-minute family. Wait at least 125 seconds from
   that login; rotations do not extend the absolute family expiry. Navigate/reload.
   Expect a refresh 401 and the login form, with a session-expired message for an
   active-session failure. A cold reload without a cookie may simply show login.
   Sign in again normally; no records should disappear.
9. **Unauthorized/failure handling.** Visit http://localhost:8080/api/transactions
   directly in a separate tab. Expect 401 even when the frontend is signed in:
   the browser address bar sends no bearer JWT, and the refresh cookie cannot
   authorize financial APIs. Try a wrong login password; expect generic 401/login
   error and a usable form. Do not test by submitting anyone else's credentials.
   A refresh POST without a CSRF header must return 403, not rotate a session.
10. **Network errors.** Using a disposable local account, stop only the backend,
    then reload the frontend. Loading should end with a connection message and
    login UI. Restart it and reload to recover. For failed logout: while signed in,
    stop the backend and click Sign out. Expect local sign-out plus an honest
    server-logout-unconfirmed message and Retry logout. Restart and click Retry;
    reload should remain signed out. Do not interrupt a real financial mutation.

After each case, report pass/fail and relevant HTTP status or a sanitized error
message. Browser Console warnings about existing responsive charts are separate
from authentication failures; record actual auth/CORS/cookie/refresh errors.

## Restore normal local testing

Stop the short-lifetime backend with Ctrl+C. Restart with normal auth lifetimes
while retaining safe auth-testing scheduler/provider controls:

```powershell
.\.local\start-auth-browser-testing.ps1 -NormalLifetimes
```

This uses PT15M access and P30D new refresh families. Sign out/in again so the new
refresh family receives the normal expiry. An old two-minute family does not
become 30 days just because configuration changed.

To return to the ordinary development startup later, exit the private launcher,
confirm the normal environment still points at local PostgreSQL, then:

```powershell
$env:DB_MIGRATION_MODE = 'validate'
.\start-dev.ps1
```

Decide explicitly whether to reenable local schedulers or paid external providers.
The original report incorrectly mentioned run.ps1; the existing backend startup
script is start-dev.ps1. No production environment values are changed.

## Final result boundaries

Database schema/history, preservation and restarted local backend connection checks
passed. The operator reported these browser results:

| Behavior | Result |
| --- | --- |
| Existing-account login and dashboard navigation | Passed; login 200 |
| Reload restoration | Passed; refresh 200 and remained signed in |
| Automatic renewal after access-token expiry | Reported passed |
| Logout remains signed out after reload; other tabs sign out | Reported passed |
| Expired refresh session returns to login | Reported passed |
| Existing financial views remain usable | Reported passed |
| New-account registration, fresh-tab restoration, offline-error handling, browser unauthorized-access checks and storage inspection | Not individually reported; do not assume manual passes |

The user asked to stop repeating completed manual tests and proceed to final
review. The checklist above remains reference material, not a request to rerun
these checks. Automated coverage exists for the unreported browser scenarios;
that does not establish an observed browser pass. No authentication bug was
reported in this integration session and no authentication source changed during
its final review, so no further test suite was run. See
[the final review](authentication-phase-1-final-review.md) for limitations and
the separate-approval deployment sequence.
