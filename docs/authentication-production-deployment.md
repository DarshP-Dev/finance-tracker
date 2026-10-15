# Authentication Phase 1 — production release procedure and completed adoption

Windows command examples use `C:\path\to\FinanceTracker`; substitute your checkout path.

Prepared October 14, 2026; completion recorded October 15, 2026.
**Production V2 adoption, application deployment and promotion are complete.**
The operator confirmed live authentication tests passed and financial features work.
See the [production release checkpoint](authentication-production-release-checkpoint.md)
for the final result, evidence boundaries and remaining limitations.
The pre-V2 investigation, backup and migration commands below are retained as
operator reference; they must not be repeated against the adopted production
database. Future deployments, promotion, resumption and other operational changes
still require their own approval. Documentation is not execution authorization.

## Reviewed release and compatibility

The [final local review](authentication-phase-1-final-review.md) records 540 backend
tests and 17 frontend tests passing, clean frontend lint/build, local strict
Flyway/Hibernate validation, 168 matching schema signatures and the reported
manual authentication results. Those results are reused; no runtime fix or
unresolved local failure requires another full-suite run.

Commit `c9e5677` contains the authentication changes: session storage/services,
JWT identity, cookies/CSRF/CORS, memory-only frontend sessions, migration safety,
regression tests and reports. Existing financial integration tests adapt to the
new unauthenticated 401 response. V1 SQL, its manifest, Vercel routing, Docker
startup and the shared baseline-validation implementation are unchanged.
Private credentials, captures, backups and browser-testing overrides remain
under ignored `.local/` directories. Any later milestone commit must also be
reviewed and scanned; do not stage private operator archives.

`V2__add_refresh_token_sessions.sql` is additive. It creates one table with eight
columns, seven constraints (including primary/unique/foreign keys), and five
indexes (including primary/unique indexes): 21 new catalog signatures. It does
not alter or delete users or any existing financial records, change amounts,
or advance existing sequences. The new foreign key references `users`; its
`ON DELETE CASCADE` removes sessions when a user is subsequently deleted, not
users when sessions are deleted. Table creation can briefly acquire locks on
`users`, so apply it during maintenance, with bounded lock waits.

The disposable migration test covers explicit BASELINE 1 -> V2, unchanged V1
schema, all nine existing table fingerprints and eight sequence states, and
validation-only startup. Production compatibility and preservation were verified
during the approved V2 operation and release. The preflight below is historical
procedure, not a request to repeat adoption. Historical production evidence is not
a future live preflight. Do not repeat V1 reconciliation or baseline registration.

## Configuration for this Vercel project

The repository uses **Next.js/React**, rather than a Vite deployment. `vercel.json`
deploys frontend and Spring Boot backend services together and rewrites `/api/*`
to the backend on the same public hostname. Keep this topology for the release.

| Setting | Production requirement |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Preserve verified production secrets; backend only; no credentials embedded in URL |
| `JWT_SECRET` | Preserve existing backend secret; at least 32 UTF-8 bytes; never a `NEXT_PUBLIC_*` variable |
| `DB_MIGRATION_MODE` | `validate` |
| `REFRESH_COOKIE_SECURE` | `true`, explicitly |
| `REFRESH_COOKIE_SAME_SITE` | `Lax` |
| `JWT_ACCESS_TOKEN_TTL` | `PT15M`; do not copy the local 60-second override |
| `REFRESH_TOKEN_TTL` | `P30D`; do not copy the local one-hour override |
| `CORS_ALLOWED_ORIGINS` | Exact canonical HTTPS application origin(s), comma-separated, no wildcard, paths or trailing slash |
| `NEXT_PUBLIC_API_BASE_URL` | Unset or empty in Production, so requests use same-origin `/api`; no localhost or separate backend URL |
| `RECURRING_SCHEDULER_ENABLED` | `false`; keep the internal Spring scheduler disabled |
| `CRON_SECRET` | Preserve backend-only existing secret for the separate Vercel cron; never print it or invoke the job during maintenance |
| Provider credentials/settings | Preserve existing backend-only settings; no new API integration is part of this release |
| `JPA_SHOW_SQL`, `JPA_FORMAT_SQL` | Keep false/unset; avoid logging financial data |

Hibernate is hardcoded to `validate` in application properties. `DB_DDL_AUTO` is
obsolete for this production runtime. Check for higher-precedence overrides such
as `SPRING_JPA_HIBERNATE_DDL_AUTO`, `SPRING_FLYWAY_*`, `SPRING_APPLICATION_JSON`,
active profiles or Java arguments; report only compliant/noncompliant/unknown,
not their secret contents. Flyway must stay enabled, clean disabled, automatic
baseline disabled, and startup validate-only. The new remote-startup guard also
rejects `migrate` on non-loopback databases; explicit operator migration is separate.

Refresh cookies are host-only, HttpOnly, Secure and scoped to `/api/auth`.
Their lifetime follows the refresh family's absolute expiry. The CSRF cookie
is also HttpOnly and scoped to `/api/auth`; `GET /api/auth/csrf` supplies the masked
token for `X-XSRF-TOKEN`. Login, registration, refresh and logout POSTs require
CSRF. Financial APIs require a bearer access JWT; a refresh cookie alone is
insufficient. The shared frontend sends credentials and obtains CSRF tokens;
do not bypass these controls to make an old frontend work.

Use one canonical hostname consistently: host-only cookies do not transfer
between custom domains and generated deployment URLs. The forwarded-header
filter is present, but preservation of original HTTPS/host, `Set-Cookie`, and
no-store headers through Vercel rewrites must be verified in the protected
production smoke test. Do not change to SameSite=None or broaden CORS without
an actual cross-site deployment requirement.

Review variable **names/scopes** privately in Vercel. Production secrets must
not be exposed in public frontend variables or newly assigned to Preview.
Historical deployments retain their own configuration; editing current scopes
does not prove that retained deployments cannot access production. The initial
preparation had not yet independently rechecked live origin, overrides,
protection, deployment identities or writer status. The final release checkpoint
records the subsequent approved checks and successful release.

## Short beta maintenance sequence

1. Obtain separate approval for the maintenance/configuration steps, backup,
   production V2 execution, commit/push and protected deployment. Record the
   reviewed source/artifact hashes privately. Warn the two other beta testers
   about downtime and a possible fresh sign-in; no automated messages are sent.
2. Confirm the production branch ID, direct non-pooler hostname and database
   `finance_tracker_db` in Neon Console against the existing private production
   procedure. SQL cannot independently identify a cloned Neon branch.
3. In Vercel, review current/retained/Preview deployments and any protection
   exceptions or automation bypasses. Confirm no other known service targets
   production. Keep local development on local PostgreSQL; close production
   SQL Editor/client tabs. Unknown targets remain unknown, with the agreed beta
   residual risk explicitly recorded; do not introduce role fencing by default.
4. Disable Vercel Cron Jobs without clicking Run; allow any in-flight execution
   to finish. Verify `RECURRING_SCHEDULER_ENABLED=false` in the deployment intended
   for release, and confirm no known running internal scheduler is writing.
   Pause the project. Verify the displayed paused/cron-off states.
5. Recheck database sessions/transactions/locks, expected V1 schema/history and
   financial/sequence fingerprints. Do not terminate unidentified sessions.
   Stop for competing activity, unexpected schema differences, checksum errors,
   ambiguous target identity, or uncertainty about the backup.
6. Create/verify the fresh recovery point below **after writers are frozen**.
   Repeat preservation/writer checks if the database changes while backing up.
7. With explicit migration approval, apply only V2 using the reviewed artifact,
   run strict validation, and compare preservation captures before any smoke
   registration/login or provider request. Stop on any unexpected result.
8. With separate release approval, commit only reviewed authentication files,
   then push the exact commit to `main`. Deploy frontend and backend **together**
   from that commit, using the protected sequence below. Do not deploy the new
   backend before V2 or expose the old frontend to the CSRF-required backend.
9. Check Ready status, Git SHA, startup logs and protected authentication/financial
   smoke tests. Keep cron off and the Spring scheduler false. Pause again if that
   is the approved stopping point; public resumption requires approval.
10. After successful release and approval, restore the intended public-access
    policy and resume the project. Reenable only the Vercel cron, separately,
    after reviewing due-transaction catch-up. Keep the internal scheduler false.
    Existing recurring processing deduplicates due occurrences and has a
    configured catch-up cap; do not invoke unreviewed manual catch-up.

Pause is an HTTP control, not proof that every background or Preview writer has
stopped. Use the existing simplified beta approach: known clients stopped,
cron disabled, scheduler disabled, session checks immediately before/after,
usable backup, and stop on observed competing writes. Privileged bypass users
and unidentified future clients remain a limited accepted risk.

## Fresh recovery point

In Neon Console, select verified production -> Backup & Restore -> Create
snapshot. Suggested name, if customizable: `production pre-auth V2 <UTC time>`.
Privately record source branch ID, snapshot name/ID, UTC creation time, availability
and expiration. Confirm it contains the current V1 baseline and the frozen
financial state. Do not select Restore, Switch over, Finalize or replace production.

Reuse the successful isolated snapshot restore workflow; a new identical rehearsal
is not required merely to repeat proven mechanics. Availability/source verification
of the fresh snapshot is still required. Do not claim the new snapshot itself
has been restore-tested unless that actually occurs. If the backup method changes
or recovery is uncertain, require one focused isolated restore verification.
[Neon's snapshot overview](https://neon.com/blog/three-ways-to-use-your-snapshots)
describes recovery into an inspection branch.

The prior plan hit Neon's one-snapshot quota. If it still prevents a new snapshot,
**do not delete the only verified snapshot**. Stop and choose an approved alternative:
additional snapshot capacity, or a fresh full custom-format `pg_dump` using the
secure production credential context and verified TLS. Keep the archive under
ignored `.local/`, include data/history/sequences, check the dump exit status and
archive listing, and restore-test into an isolated local database if this is the
backup being relied upon. A schema-only export or readable archive listing is
not a tested recovery backup. This is a backup fallback, not a new Neon rehearsal.

The older October 12 pre-adoption snapshot is not automatically a current V2
rollback point: it predates baseline registration and later normal application
activity. Any decision to reuse it requires dated comparisons and explicit
acceptance of lost changes; do not silently substitute it for the fresh backup.

## Preflight and exact migration commands

Historical V2 procedure: production has already completed this step. Do not rerun
these pre-V2 commands against it. For a separately approved future environment,
build from reviewed source **before** loading
production credentials, with local DevTools processes stopped to prevent an
unintended development restart. No need to rerun the already-passing full suite
unless source changes or a new failure requires it.

```powershell
cd C:\path\to\FinanceTracker\backend\backend
.\mvnw.cmd -B -DskipTests package
if ($LASTEXITCODE -ne 0) { throw 'Release build failed' }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$releaseJar = (Resolve-Path 'target/backend-0.0.1-SNAPSHOT.jar').Path
$archive = [IO.Compression.ZipFile]::OpenRead($releaseJar)
try {
    $packagedSql = @($archive.Entries.FullName | Where-Object {
        $_ -like 'BOOT-INF/classes/db/migration/*' -and $_ -like '*.sql'
    } | Sort-Object)
    $expectedSql = @(
        'BOOT-INF/classes/db/migration/V1__initial_schema.sql',
        'BOOT-INF/classes/db/migration/V2__add_refresh_token_sessions.sql'
    )
    if (@(Compare-Object $expectedSql $packagedSql).Count -ne 0) {
        throw 'Artifact must contain exactly reviewed V1 and V2 SQL'
    }
} finally { $archive.Dispose() }
$releaseJarHash = (Get-FileHash -LiteralPath $releaseJar -Algorithm SHA256).Hash
# Record this hash privately; do not rebuild/change the artifact during execution.
```

Preflight must show exactly one successful BASELINE version 1, no applied SQL
migrations, no refresh-session table, and 147 canonical V1 signatures. Reuse the
existing private read-only catalog/fingerprint capture workflow, pinned to the
verified production endpoint. Capture all nine table counts/hashes and all eight
sequence `last_value`/`is_called` states immediately before migration.

Useful read-only metadata queries (do not print financial records, role names,
raw query texts or credentials; retain results privately):

```sql
BEGIN READ ONLY;
SELECT current_database(), current_setting('server_version');
SELECT installed_rank, version, type, script, checksum, success
  FROM public.flyway_schema_history ORDER BY installed_rank;
SELECT to_regclass('public.refresh_token_sessions') IS NULL AS v2_not_applied;
SELECT pid, application_name, client_addr, state,
       clock_timestamp() - backend_start AS connection_age,
       xact_start IS NOT NULL AS in_transaction,
       state = 'active' AS active
  FROM pg_stat_activity
 WHERE datname = current_database() AND pid <> pg_backend_pid();
SELECT l.pid, l.locktype, l.mode, l.granted
  FROM pg_locks l JOIN pg_stat_activity a ON a.pid = l.pid
 WHERE a.datname = current_database() AND a.pid <> pg_backend_pid();
ROLLBACK;
```

With the new artifact, migration information should be BASELINE 1,
V1 BASELINE_IGNORED and **only V2 PENDING**. Pre-V2 strict validation is expected
to reject pending V2; do not repair/baseline to suppress this. If V2 already
exists, stop to inspect/validate that state instead of applying it again.

The following block uses the existing operator CLI without starting Spring,
schedulers or providers. It is the prebuilt equivalent of
`.\database.ps1 -Command <verify|migrate|validate> -AllowRemote`, which otherwise
rebuilds the artifact on each invocation. The prebuilt route keeps the reviewed
artifact fixed. `verify` compares **V1 only**; do not use it after V2.

Run the block separately for these exact operations, in this order:

1. `$authDatabaseOperation = 'verify'` — read-only canonical V1 preflight.
2. `$authDatabaseOperation = 'migrate'` — **only after explicit V2 approval and
   the complete frozen preflight/backup**. It applies all pending migrations,
   so the artifact and history checks above are mandatory.
3. `$authDatabaseOperation = 'validate'` — strict read-only history/checksum
   validation after V2. V2 schema/preservation checks are additional, not replaced
   by Flyway checksum validation.

```powershell
# Choose only the currently approved stage. Safe initial choice:
$authDatabaseOperation = 'verify'
$authProductionHost = '<console-verified-direct-production-hostname>'
if ($authProductionHost -notmatch '^ep-[a-z0-9-]+\.[a-z0-9.-]+\.neon\.tech$' -or
    $authProductionHost -match '-pooler\.') {
    throw 'Set the privately reconfirmed direct production endpoint first'
}
if ($authDatabaseOperation -notin @('verify', 'migrate', 'validate')) {
    throw 'Unsupported operation; baseline, repair and clean are excluded'
}
if ((Get-FileHash -LiteralPath $releaseJar -Algorithm SHA256).Hash -ne $releaseJarHash) {
    throw 'Reviewed release artifact changed'
}
$savedAuthDbEnvironment = @{}
foreach ($variableName in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD')) {
    $savedAuthDbEnvironment[$variableName] =
        [Environment]::GetEnvironmentVariable($variableName, 'Process')
}
$authProductionLogin = $null
try {
    $authProductionLogin = Import-Clixml -LiteralPath '.local/neon-production-credential.clixml'
    if ($authProductionLogin -isnot [Management.Automation.PSCredential]) {
        throw 'Expected the existing Windows-encrypted production credential'
    }
    $env:DB_USERNAME = $authProductionLogin.UserName
    $env:DB_PASSWORD = $authProductionLogin.GetNetworkCredential().Password
    # TLS verifies host/certificate using Java trust roots, as in the V1 procedure.
    $authDbOptions = '-c lock_timeout=5000 -c statement_timeout=60000'
    if ($authDatabaseOperation -ne 'migrate') {
        $authDbOptions += ' -c default_transaction_read_only=on'
    }
    $encodedAuthDbOptions = [Uri]::EscapeDataString($authDbOptions)
    $env:DB_URL = 'jdbc:postgresql://' + $authProductionHost + ':5432/finance_tracker_db' +
        '?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&options=' +
        $encodedAuthDbOptions
    & java '-Dloader.main=com.financetracker.backend.migration.DatabaseMigrationTool' `
        -cp $releaseJar org.springframework.boot.loader.launch.PropertiesLauncher `
        $authDatabaseOperation
    if ($LASTEXITCODE -ne 0) {
        throw 'Database command failed; stop and inspect the outcome before retrying'
    }
} finally {
    foreach ($variableName in $savedAuthDbEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($variableName,
            $savedAuthDbEnvironment[$variableName], 'Process')
    }
    $authProductionLogin = $null
}
```

Do not print the credential object, environment values or connection URL, enable
shell tracing, or paste raw logs externally. If TLS fails, stop; never downgrade
certificate verification. Bounded lock/statement timeouts are execution-session
options, not permanent database settings. Flyway also obtains its migration lock;
a blocked/failed command is not permission to terminate other clients.

Postflight: exactly two successful history rows (BASELINE 1 and SQL V2), with V1
SQL still not executed; migration info BASELINE 1, ignored V1 and successful V2;
no pending/missing/failed/unexpected state; strict validation successful. Compare
168 catalog signatures (147 unchanged V1 + 21 V2), confirm validated constraints,
and unchanged nine-table hashes/counts/eight sequence states. The new table should
be empty until approved authentication tests begin. Migration tests establish
transactional V2 behavior, but always inspect actual history/schema after an
interrupted command before considering a retry. Never edit history or V1/V2 SQL.

## Protected deployment and the pause issue

The [V1 release checkpoint](neon-production-backend-release-checkpoint.md) records
that pausing blocked the Git-triggered build. Assume that can recur; a paused
push does not prove the corrected application was built or deployed.

Vercel now documents All Deployments Vercel Authentication as free on every plan
([September 9, 2026 announcement](https://vercel.com/changelog/protect-production-deployments-for-free-on-every-plan)).
Verify availability and effective coverage in this project rather than relying
on an older pricing statement or the historic setting. Review exceptions and
bypasses, production domain, retained deployment URLs and Preview URLs.

A paused `503 DEPLOYMENT_PAUSED` response masks the authentication gate and cannot
prove protection works. Prefer establish/check the gate before pausing, using
anonymous requests with no cookies, authorization or bypass headers. The protected
operator browser must not make financial requests to the old deployment after V2.

When migration/postflight are complete and the exact commit is ready, if deployment
is blocked while paused, obtain approval for **temporary protected resume ->
immediate deployment/redeployment of the approved authentication commit -> startup
and smoke checks -> pause again or separately approved public resumption**.
Resuming itself restores the existing deployment without requiring a redeploy
([Vercel project controls](https://vercel.com/docs/projects/managing-projects)).
Do not resume unprotected and hope the new backend arrives first. Stop if the
gate cannot be established, or if an anonymous probe reaches the application.

Deploy both services from the reviewed `main` commit using current Production
settings. Verify the resulting deployment SHA, Ready status, both build logs,
database connection, successful strict Flyway BASELINE 1/V2 validation, Hibernate
validation and completed Boot startup. Do not count a Ready frontend alone as
backend success. Review errors without publishing raw authentication/DB content.
Keep Vercel cron disabled through redeployment and recheck that state afterward.
Automatic production-domain assignment is currently disabled. A Ready deployment
therefore does not establish that the canonical domain serves it. Keep that
setting unchanged; explicitly promoting the reviewed deployment is a separate
approved action. The release checkpoint specifies the proposed protected sequence.
[Vercel cron controls](https://vercel.com/docs/cron-jobs/manage-cron-jobs) are separate
from Spring scheduling; disabling future invocations does not cancel a job already
running. Do not create automatic migration/deployment scripts or new DB roles.

## Short protected production smoke checklist

Use approved beta test accounts on the canonical HTTPS hostname. Registration,
login, refresh and logout intentionally write account/session state and therefore
need release smoke-test approval; preservation comparison must finish first.

| Test | Expected result |
| --- | --- |
| Existing account login | CSRF bootstrap succeeds, login 200, dashboard opens; refresh cookie HttpOnly/Secure/Lax/host-only at `/api/auth`; no-store auth responses |
| One new test registration, then login | Registration 201; subsequent login 200; no accidental access to another account's data |
| Reload and new tab restoration | Refresh 200 restores the same account without password; cookies retained through the Vercel rewrite |
| Access renewal | After normal 15-minute access expiry, a financial read transparently refreshes once; no refresh storm or unexpected CSRF error |
| Logout with two tabs open | Logout 204; both tabs sign out; reload remains signed out; the revoked refresh family cannot restore a session |
| Unauthorized and financial access | No bearer JWT -> backend 401; authenticated transactions/budgets/investments/analytics usable and scoped to the account; refresh cookie alone grants no financial access |
| Failed login | Clear failure with no session created; existing page does not crash; no credentials/tokens in visible logs |

Do not shorten production token lifetimes for testing. Normal access expiry is
15 minutes; refresh expiry was exercised locally. Do not wait 30 days or introduce
a production expiry override merely to repeat that local test. Distinguish Vercel's
authentication challenge from the application's own 401. Never export Network
HARs, response access JWTs, CSRF/cookie values or passwords to Git/reports.

## Session impact and rollback

Accounts/passwords and financial data are preserved. Beta users should reload
old tabs and may need one fresh sign-in: legacy localStorage JWTs do not establish
refresh sessions and the new frontend removes legacy storage. Old frontend code
does not supply the new CSRF flow and can receive 403 until reloaded. Host-only
cookies mean switching hostname also requires a separate sign-in.

Preserve `JWT_SECRET`; changing it would invalidate existing access tokens.
Previously issued JWTs retain their original expiry. Logout revokes refresh
sessions but does not immediately invalidate an already-issued access JWT;
normal new access exposure lasts at most its remaining 15-minute lifetime.
Refresh families expire absolutely after 30 days and rotation does not extend
them. Concurrent refresh without Web Locks or a lost refresh response can require
fresh login, as documented in the local review.

If V2 fails: keep maintenance controls active, inspect its transaction/history,
and fix the cause before retrying. If deployment fails after V2: keep protection,
pause again, keep cron off, and prefer a reviewed **V2-aware forward fix**. Returning
only to a V1-only strict backend is not a safe rollback because it can reject
V2 history; frontend-only rollback also conflicts with CSRF/session behavior.

Database recovery is an explicitly approved fallback: restore the fresh pre-V2
backup into an isolated target, verify schema/history/data, then separately approve
a switch to that verified V1 database plus the matching baseline-compatible old
frontend/backend. Recovery loses writes since backup, including new accounts and
sessions; users must sign in again. Do not drop session tables/history, run repair,
clean, rebaseline, or automatically finalize a Neon restore to force rollback.

## Original prerequisites and approval boundaries

Production V2, its preservation checks, deployment, promotion and operator-confirmed
live testing have completed. The following original checklist remains historical
context; use the release checkpoint for the final outcome and future follow-ups.

- Privately reconfirm current production identity, deployment SHA, canonical
  origin(s), effective environment overrides/scopes and protection coverage.
- Approve maintenance/configuration changes; confirm cron off, scheduler false,
  stopped known clients, session preflight and the limited beta residual risks.
- Obtain a fresh usable backup, or approve/verify the dump fallback if quota blocks
  snapshots. Retain the previous recovery point; record dated preservation captures.
- Review/freeze the release artifact and pending migration list, then explicitly
  approve production V2 execution. The original preparation itself made no writes.
- Separately approve secret-clean staging/commit/push and the protected deployment
  sequence, including temporary resume if required and intentional smoke writes.
- Approve final public access and Vercel cron resumption only after release checks.

No new migration, application code change, infrastructure account or role-fencing
system is part of this documentation finalization. The completed release uses the
implementation in `c9e5677`; no live configuration or financial data is changed here.
