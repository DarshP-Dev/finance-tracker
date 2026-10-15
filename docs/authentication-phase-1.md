# Authentication Phase 1 — local implementation and review

Windows command examples use `C:\path\to\FinanceTracker`; substitute your checkout path.

Status: implementation and local verification are complete; production Flyway V2
has subsequently been adopted and verified. Protected application activation is
still a separate stage. See the [production release checkpoint](authentication-production-release-checkpoint.md).
The implementation/testing evidence below describes the earlier local work;
no production action occurred during that initial implementation stage.
Automated database verification used newly created, guarded disposable PostgreSQL
17 clusters. The actual frontend is Next.js/React/TypeScript; it was preserved.

Local integration inspection on October 14 subsequently found that the existing
local database had already recorded SQL V2 at database timestamp
2026-10-14 00:14:19. The original statement that this database had not been
migrated was not independently verified and is withdrawn. No explicit local
migrate/baseline command was run during authentication implementation or the
initial integration inspection. An already running development backend can apply
V2 because start-dev.ps1 defaults to migrate; the exact initiating event has not
yet been confirmed. Do not apply or baseline V2 again. Use strict history validation
and a V1-plus-V2 schema comparison for this database. No production action occurred.

## Architecture and behavior

Existing BCrypt registration, login, JWT signing, authenticated APIs, and UI remain.
Previously, JWTs lasted one hour, lived in localStorage, and browser logout did not
revoke anything server-side. The API client also treated authorization failures
(`403`) as session expiration.

New access JWTs default to **15 minutes**. The existing HMAC-SHA256 implementation
now checks the signed algorithm/type header, signature, expiration and immutable
account ID as well as email. Financial user lookup uses the verified account ID.
This prevents a token belonging to an old account from authorizing another account
that subsequently uses its email. Existing JWTs already contain the required ID.

Login issues an opaque 256-bit random refresh token. Only its SHA-256 hash is
stored. The raw token is returned exclusively in a host-only HttpOnly cookie,
never in JSON, localStorage, database rows, diagnostic logs or default object
string representations. A family has a fixed **30-day absolute expiry** by
default; rotation does not extend it indefinitely.

Refresh acquires a stable PostgreSQL owner-row lock, then loads and validates the
current token record. Rotation consumes that record and creates one successor
within the same transaction. Replaying a consumed token revokes every still-active
member of that family. The rejection does **not** roll back its revocation.
Two simultaneous uses of one token yield at most one success; the second use
revokes the family, including the first request's successor. This is intentionally
fail-closed, rather than allowing a grace window that could hide token theft.

Logout revokes the current family, including when it presents a consumed member,
and clears the cookie. Missing/unknown cookies and repeated logout are safe.
Password changes revoke **all** refresh families for that user and clear the
current cookie. Independent logins have independent families. Refresh sessions
survive backend restarts because their state lives in PostgreSQL.

Registration preserves the existing explicit-sign-in UX: `201` creates the account
and returns the existing access-token response, but does not start a persistent
refresh session. Login starts that session. Registration conflict messages are
generic; invalid login retains its generic email/password error and the existing
AuthenticationManager's dummy-password check for unknown emails.

## API and browser security

| Endpoint | Request | Result |
| --- | --- | --- |
| `GET /api/auth/csrf` | Credentialed same-origin/allowed-origin request | Masked `token` and `headerName`; HttpOnly CSRF cookie; no-store |
| `POST /api/auth/register` | Existing validated JSON plus CSRF header/cookie | Existing `201` AuthResponse, now also `expiresIn` seconds |
| `POST /api/auth/login` | Existing validated JSON plus CSRF header/cookie | `200` AuthResponse plus refresh cookie |
| `POST /api/auth/refresh` | Refresh cookie and CSRF header/cookie; no user ID/token in body | `200` AuthResponse plus rotated cookie, or generic `401` with cookie cleared |
| `POST /api/auth/logout` | CSRF header/cookie; refresh cookie if present | Idempotent `204`, family revoked and cookie cleared |
| `PUT /api/account/password` | Existing bearer JWT and current/new password | `204`, refresh families revoked and cookie cleared |

`AuthResponse` retains `accessToken`, `tokenType`, `userId`, `username`, `email`,
and adds `expiresIn`. Refresh never trusts a submitted user ID.

Cookie defaults: `Path=/api/auth`, HttpOnly, SameSite=Lax, no Domain attribute.
Secure is automatic from the HTTPS request, including the existing forwarded-header
handling behind Vercel. Production should explicitly configure Secure=true.
Refresh and CSRF cookies use the same configured SameSite policy; None requires
Secure=true. Only trust forwarded headers behind the intended trusted proxy.

Vercel rewrites frontend/API requests through one origin. Local development uses
localhost:3000 and localhost:8080, which are cross-origin but same-site. Use the
same hostname consistently; do not mix localhost with 127.0.0.1. Credentialed CORS
continues to allow only configured exact origins and now allows X-XSRF-TOKEN.

All unsafe `/api/auth/` requests, including login and registration, require CSRF.
Financial APIs remain bearer-only; a refresh cookie cannot authenticate them.
Their existing calculations and mutation handlers were not changed.

Missing/invalid access authentication returns `401` with the security-boundary code
`AUTHENTICATION_REQUIRED`. CSRF failures return `403`/`CSRF_INVALID`; other access
denials remain `403`. The frontend never retries business-level 401s, 403s, network
errors, timeouts or server errors. This distinction prevents replaying mutations
whose financial handler may already have executed.

These choices follow [Spring Security's CSRF guidance](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
and [OWASP session management guidance](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html).

## Frontend session handling

- Access token and account information stay in module memory. Legacy token/user
  localStorage keys are removed; no access or refresh token is persisted there.
- Initial page load restores the session via the refresh cookie with a bounded
  loading state. A missing session shows the existing login UI. Current routes are
  preserved, so signing back in can return to the selected section.
- Concurrent requests share one refresh promise. A late rejection uses an already
  renewed access token instead of rotating again. Each rejected request is retried
  at most once, and successful financial mutations notify existing UI listeners
  only once.
- Web Locks serialize cookie-changing requests across tabs when supported.
  Waiting for a lock is bounded to 20 seconds; each auth HTTP request has a
  10-second timeout. No polling or automatic retry loop is introduced.
- Logout clears local state immediately and informs other tabs through a nonsecret
  storage marker/BroadcastChannel. Request/session generations prevent in-flight
  refreshes or old account responses from restoring a signed-out session.
- A failed server logout is reported honestly and provides a Retry logout button.
  Refresh connectivity errors leave loading and show a connection message.
- Password change signs out the frontend and asks for a fresh login.

## Migration and retention

`V2__add_refresh_token_sessions.sql` adds one table and five indexes including
primary-key/hash uniqueness indexes. Its eight columns contain UUID row/family IDs,
user FK, hash and timezone-aware creation/expiry/revocation/rotation timestamps.
Checks enforce hash shape, positive lifetime and valid revocation/rotation state.
Account deletion cascades only its session rows.

No V1 SQL, V1 manifest, or existing history entry was edited. Tests compare all 147
V1 signatures independently of the new table, verify V2 separately, and confirm
all nine financial table contents and eight sequence states remain unchanged.
Historical V1 regression tests resolve only their own isolated copy of V1.

Consumed/revoked rows are retained for replay detection through absolute expiry.
There is no new cleanup scheduler. Expired rows remain until an operator-approved
session-only retention job is added; inactive families therefore consume storage.
An eventual cleanup can delete rows whose expires_at is in the past, but must never
remove unexpired consumed rows. No cleanup was executed in this task.

Startup still requires Hibernate validate and Flyway validation by default.
Additionally, startup `migrate` mode is rejected for remote database URLs; remote
migrations must use the explicit operator tool. The CLI's migration operation is
unchanged. Pending V2 fails strict validation instead of silently creating tables.
The V1 schema-adoption `verify` command remains a **V1-only** comparison: after V2,
use migration-history `validate`, not the old V1-only schema verifier.

## Configuration

| Variable | Default / requirement |
| --- | --- |
| `JWT_SECRET` | Existing backend secret, at least 32 bytes; keep stable across restarts |
| `JWT_ACCESS_TOKEN_TTL` | `PT15M`; positive ISO duration, at least one second |
| `REFRESH_TOKEN_TTL` | `P30D`; positive ISO duration |
| `REFRESH_COOKIE_SAME_SITE` | `Lax`; supports Lax, Strict, None |
| `REFRESH_COOKIE_SECURE` | `auto`; use `true` on production HTTPS; `false` is rejected on HTTPS requests |
| `CORS_ALLOWED_ORIGINS` | Existing exact-origin list; local default http://localhost:3000 |
| `NEXT_PUBLIC_API_BASE_URL` | Existing frontend setting; local default http://localhost:8080; production same-origin |
| `DB_MIGRATION_MODE` | Keep `validate`; startup migrate is loopback-only |
| `DB_DDL_AUTO` | Must remain `validate` |

No new secrets or third-party auth/framework dependencies are needed. Refresh
lifetime changes affect newly issued families; existing families retain their
original absolute expiry.

## Verification

Final results: **540 backend tests passed**, zero failures/errors/skips; **17
frontend tests passed**; frontend lint passed with no warnings/errors; frontend
production build passed, including TypeScript and all 14 generated pages.
Repeated real Spring Boot startup against a baselined disposable database passed
with database-enforced read-only connections, external providers and schedulers
disabled. V2 was explicitly applied only during disposable setup. Commands:

```powershell
cd C:\path\to\FinanceTracker\backend\backend
.\run-tests.ps1

cd C:\path\to\FinanceTracker\frontend\finance-tracker-frontend
npm test
npm run lint
npm run build
```

The runner creates and stops its own disposable PostgreSQL cluster on a random
loopback port, with synthetic records, providers disabled and schedulers off.
Tests cover hash-only storage, login/signup, expiration, rotation, replay commit,
concurrency, family isolation, idempotent logout, password-change revocation,
immutable ownership/email reuse, CSRF, CORS, cookie attributes, restart persistence,
V2 adoption/data preservation and repeated full validation-only startup.
Frontend tests use Node's built-in test runner, the existing TypeScript compiler
and Axios mock adapters; no new testing framework or external requests are used.
They cover reload restoration, login, expiry, failures, logout, tab logout,
single-flight refresh, one retry, stale responses and exactly one financial write
and UI notification.

Initial failures were reported and fixed: CSRF failures were routed through the
anonymous authentication entry point, V1 index signatures needed the verifier's
existing normalization, and six older tests expected anonymous 403 instead of
401. A Windows source-encoding issue was also fixed. Sandbox restrictions required
running the guarded local tests/build outside the sandbox; no production access
was involved.

## Manual local checks

1. Review V2 and back up your local database. Explicitly verify the migration tool
   targets loopback/local PostgreSQL before applying anything. The existing local
   database now already has V2; validate it without migrating or baselining again.
2. With your existing local-only configuration, explicitly run:
   `./database.ps1 -Command validate`. Apply migrate only to a separately verified
   local database that genuinely has pending V2 and separate operator approval.
   Do not baseline again. Do not use a remote connection for local testing.
3. Start the backend with the existing start-dev.ps1 procedure, explicitly setting
   DB_MIGRATION_MODE=validate, and the frontend with
   `npm run dev`. Keep providers and schedulers disabled for auth-only testing.
4. Register an account; confirm the existing prompt asks you to log in. Log in,
   navigate to Analytics/Investments, reload, and confirm the session restores
   without putting tokens in localStorage.
5. Inspect cookie metadata privately: HttpOnly, Path=/api/auth, SameSite=Lax;
   Secure on HTTPS. Do not copy cookie values or JWTs into reports.
6. In an isolated test configuration, shorten access TTL, wait for expiry, then
   trigger multiple reads and one test mutation. Confirm one refresh and one
   successful financial mutation. No network-error retry should occur.
7. Log out and reload. Try two tabs: logout in one should sign the other out.
   Change a test account password and confirm old refresh sessions are rejected.
8. Disconnect the backend during restore/logout. Check loading ends, errors remain
   usable, and Retry logout works when connectivity returns. Confirm existing
   transaction, budget, investment, analytics and recurring UI still behaves normally.

Browser automation was not performed against the live app. Real HTTP/backend
tests and frontend adapter tests are automated; visual/browser checks above remain
operator actions.

## Safe future production deployment — separate approval required

1. Review this diff and approve the release separately. Keep public access paused,
   Vercel Cron Jobs disabled and RECURRING_SCHEDULER_ENABLED=false during rollout.
2. Confirm the intended production branch/database and stop writers. Obtain and
   verify a usable recovery point, record financial fingerprints/sequences and
   current BASELINE 1 history. Do not repeat V1 baseline or reconciliation.
3. Build the reviewed artifact containing V2. Using the existing secure operator
   connection procedure and separate explicit migration approval, run
   `./database.ps1 -Command migrate -AllowRemote`. This should apply only pending
   V2. Never set remote startup migration mode to migrate.
4. Run `./database.ps1 -Command validate -AllowRemote`; inspect one successful
   BASELINE 1 plus one successful SQL V2 entry, no V1 schema SQL execution and no
   pending/failed/missing/unexpected migrations. Verify unchanged financial data
   and sequence states, and the new empty session table.
5. Deploy the compatible backend **and frontend together**, with Hibernate/Flyway
   validate-only, secure cookies, correct same-origin routing/CORS and stable JWT
   secret. A backend released before V2 correctly refuses startup. An older V1-only
   backend restarted after V2 can also fail strict history validation, so do not
   resume the old artifact after the database upgrade.
6. Verify startup and authentication smoke tests privately; check cookie/CSRF,
   refresh, ownership and logout. Existing open tabs should reload the updated
   frontend and sign in once: legacy stored JWTs do not create refresh sessions.
   Resume public access and recurring processing only with separate approval.
7. If deployment fails, keep maintenance controls active. Prefer a corrected
   V2-aware build. Full database recovery and a matching old application build are
   an operator-approved fallback. Do not delete V2 history/table, rebaseline,
   repair or clean to force an incompatible rollback.

## Remaining limitations / later phases

- Issued access JWTs remain valid until expiry after logout, replay revocation or
  password change; this phase does not maintain an access-token denylist. New JWTs
  default to 15 minutes; already issued one-hour JWTs retain their original expiry.
- Memory storage/HttpOnly reduce token exposure, but do not eliminate XSS. Do not
  claim that malicious code running in the application origin cannot act as a user.
- Without Web Locks, refreshes remain single-flight within a tab but concurrent
  tabs can trigger strict replay revocation and require sign-in again.
- A timeout/lost refresh response can leave a rotated successor inaccessible;
  fail-closed behavior may require a fresh login. Network failure does not prove
  server-side logout succeeded, so the UI explicitly offers a retry.
- Expired-session housekeeping and general auth rate limiting are follow-up work;
  no email verification, password-reset delivery, social login, MFA or device UI
  was added.

## Files

Created (all paths relative to repository root):

- `backend/backend/src/main/java/com/financetracker/backend/entities/RefreshTokenSession.java`
- `backend/backend/src/main/java/com/financetracker/backend/repositories/RefreshTokenSessionRepository.java`
- `backend/backend/src/main/java/com/financetracker/backend/security/AccountPrincipal.java`
- `backend/backend/src/main/java/com/financetracker/backend/security/RefreshCookie.java`
- `backend/backend/src/main/java/com/financetracker/backend/services/AuthenticationSessionService.java`
- `backend/backend/src/main/java/com/financetracker/backend/services/RefreshTokenService.java`
- `backend/backend/src/main/resources/db/migration/V2__add_refresh_token_sessions.sql`
- `backend/backend/src/test/java/com/financetracker/backend/migration/AuthenticationMigrationTests.java`
- `backend/backend/src/test/java/com/financetracker/backend/migration/AuthenticationSessionIntegrationTests.java`
- `backend/backend/src/test/java/com/financetracker/backend/migration/RemoteStartupMigrationSafetyTests.java`
- `backend/backend/src/test/java/com/financetracker/backend/security/JwtSessionSecurityTests.java`
- `frontend/finance-tracker-frontend/src/lib/auth-session.ts`
- `frontend/finance-tracker-frontend/scripts/run-auth-tests.mjs`
- `frontend/finance-tracker-frontend/tests/auth-session.test.mjs`
- `docs/authentication-phase-1.md`

Modified backend source/config: AccountController, AuthController, AuthResponse,
UserRepository, CustomUserDetailsService, JwtService, SecurityConfig, AccountService,
AuthenticatedUserService, AuthService, DatabaseMigrationConfiguration,
application.properties. Temporary auth request diagnostics are no longer registered
or configured to log at debug level; the unused original filter class is unchanged.

Modified backend tests: AccountServiceTests, SecurityConfigTests,
BaselinedBackendStartupTests, FlywayApplicationIntegrationTests, FlywayMigrationTests,
MigrationHistoryValidationTests, ReconciledSchemaApplicationTests,
ReconciliationTestDatabase, FinancialInsightsIntegrationTests,
FinancialInsightsSummaryIntegrationTests, InvestmentPortfolioIntegrationTests,
NotificationIntegrationTests, market/MarketQuoteSnapshotIntegrationTests.
The last five changes adjust only anonymous/invalid JWT status expectations.

Modified frontend: eslint.config.mjs (ignore generated local test output),
package.json (built-in test command), src/app/page.tsx,
src/components/transactions/TransactionDashboard.tsx, src/lib/api.ts.
No production credentials, captures, financial records or generated build/test
output belong in Git; test outputs remain beneath ignored .local directories.
