# Authentication Phase 1 — final local review

Windows command examples use `C:\path\to\FinanceTracker`; substitute your checkout path.

Review date: October 14, 2026. Local implementation and essential integration
checks are complete. This document records the local review, during which no Neon
production connection, production migration, Vercel setting change, commit, push
or deployment occurred. The subsequent approved production V2 adoption is recorded
in the [production release checkpoint](authentication-production-release-checkpoint.md).
Phase 1 was subsequently deployed, promoted and confirmed working in live manual
testing by the operator on October 15, 2026. The local evidence below remains a
historical record; it is not the current production release status.

## Confirmed behavior

| Evidence | Result |
| --- | --- |
| Existing local account login | Operator confirmed login 200 and dashboard navigation |
| Page reload restoration | Operator confirmed refresh 200 and remained signed in |
| Automatic renewal after access JWT expiry | Operator reported passed |
| Logout remains signed out after reload and synchronizes other tabs | Operator reported passed |
| Refresh-session expiry returns to login | Operator reported passed |
| Existing financial views remain usable | Operator reported passed |
| Local startup | Operator reported no Flyway/Hibernate errors; listener checks observed local PostgreSQL connections and no non-loopback established connections |
| Local CORS/CSRF settings | Local HTTP checks passed: exact localhost:3000 origin, credentials allowed, HttpOnly /api/auth SameSite=Lax CSRF cookie, Secure false on HTTP |

New-account registration, opening a fresh tab after closing the old one, offline
error/logout-retry handling, browser unauthorized-access checks and direct storage
inspection were not individually reported. They remain **unverified manually**.
Relevant backend/frontend automated coverage passed; do not substitute that for
an observed browser result. No repetition of completed operator tests is required.
Production HTTPS cookies and deployed-origin behavior were outside this local
review. Subsequent operator-confirmed live authentication results are recorded in
the production release checkpoint; no claim is made that every optional browser
scenario or cookie attribute was independently inspected in production.

## Database outcome and evidence limits

The local database already had successful **BASELINE 1** and **SQL V2** history
when inspected. V2's database timestamp is 2026-10-14 00:14:19. It was not reapplied,
baselined again, repaired or cleaned. Current strict Flyway history/checksum
validation passed. Its three migration-info entries include the baseline, ignored
V1 script and applied V2; actual history contains two successful rows.

Read-only catalog comparison matched **147 V1 signatures plus 21 V2 signatures**,
with no unexpected/missing/changed definitions. V1 SQL and its manifest are
unchanged. V2 adds only refresh sessions and their indexes/constraints; it has no
DML against accounts or finances and does not change existing financial amounts.

All nine existing table counts/full-row hashes and eight sequence states matched
across the integration inspection. The initial capture was already after V2;
this does not independently prove a retrospective pre-V2 versus post-V2 data
comparison. A fresh local pg_dump archive was created under ignored .local and
its listing was verified readable (11 table-data entries). No restore was run.

The earlier implementation report's claim that the existing local database had
not been migrated was unverified and has been corrected. The running JVM predated
V2, included DevTools and was launched through spring-boot:run. start-dev.ps1
defaults to local migrate when no mode is set; an automatic development restart
during compilation is a plausible explanation for V2 application. Available saved
logs do not prove the initiating event. The testing launcher now explicitly uses
validation-only startup and a pinned local datasource.

## Automated verification and test-count audit

Latest full implementation run: **540 backend tests passed**, zero failures,
errors or skips. **17 frontend tests passed**, lint had no errors/warnings, and
the production build/TypeScript checks passed. These are the prior verified runs;
the final review did not rerun them. No authentication code fix or unresolved
reported failure required a focused rerun, and no full-suite rerun was performed.

The published Flyway milestone suite was **515**, not 555. The earlier 555 workspace
run also included 40 private operator-preparation cases (13 rehearsal, 13 production
read-only, 14 reconciliation). Those remain archived intact. Authentication added
25 cases: 1 migration, 16 HTTP/session integration, 7 JWT/cookie and 1 remote-startup
safety case. **515 + 25 = 540**. No tracked test files or methods were removed,
no new @Disabled annotations were introduced and Surefire filtering was unchanged.

Stale XML reports for those private cases and 15 unused role-fencing cases remain
on disk. Summing every old report gives 595 and is not the executed-suite count.
The actual full-run log and its executed class list confirm 540. Private historical
preparation cases are not evidence of missing authentication/financial regressions.

## Remaining bugs and security limitations

No confirmed authentication defect remains from the reported local tests. This
does not establish that every possible browser/runtime scenario is bug-free.

- Access JWTs remain valid until their original expiry after logout, password
  change or refresh-family revocation. Normal new access lifetime is 15 minutes;
  older already-issued one-hour JWTs retain their original expiry. There is no
  immediate access-token denylist.
- Without Web Locks, same-tab refresh remains single-flight, but concurrent tabs
  can trigger strict token-reuse revocation and require fresh sign-in. Lost refresh
  responses/timeouts can also require sign-in; no permissive replay grace window
  is provided.
- Server logout cannot be guaranteed during a network failure; the UI reports
  this honestly and offers Retry logout. Its browser failure path is unreported.
- HttpOnly cookies and memory-only access tokens reduce exposure but do not
  eliminate XSS. General auth rate limiting and expired-session cleanup are
  follow-up work. Consumed tokens must remain stored until family expiry for
  replay detection; there is no new cleanup scheduler.
- Local start-dev.ps1 still defaults to migrate. Explicitly set validate for normal
  development to avoid another automatic schema application when future migrations
  are introduced. Do not change migration history to hide this behavior.

## Restore normal local configuration

The current browser-testing launcher uses PT60S access and PT1H new refresh
families, with schedulers/providers disabled. Stop it with Ctrl+C and restart:

```powershell
cd C:\path\to\FinanceTracker\backend\backend
.\.local\start-auth-browser-testing.ps1 -NormalLifetimes
```

Normal lifetimes are PT15M/P30D. Sign out/in once to issue a normal-lifetime refresh
family; restarting does not extend an existing short family. The launcher restores
the caller's environment when it exits and changes no permanent property file.
For ordinary local startup afterward, use DB_MIGRATION_MODE=validate with
start-dev.ps1 and confirm the datasource remains local. Reenable providers or
scheduled processing only deliberately; no production environment is changed.

## Original deployment requirements — historical local review

These steps describe the release requirements before production adoption. V2,
deployment and promotion have since completed; do not repeat the migration step.

1. Review source and credentials exclusions; obtain separate commit/push/release
   approval. A push to main can trigger Vercel, so prevent premature deployment.
2. Confirm the production target and a usable backup; pause writers/cron using the
   established beta maintenance procedure. Retain the existing BASELINE 1.
3. With separate production migration approval, explicitly apply only pending V2
   using the reviewed current artifact/operator tool, then run strict validation
   and preservation checks. Never rebaseline or execute V1 again.
4. Deploy backend and frontend together. Keep Flyway/Hibernate validate-only;
   remote startup migrate is blocked. The updated backend requires V2 beforehand,
   and the old V1-only backend can reject V2 history if restarted afterward.
5. Set production refresh cookies Secure=true, retain appropriate SameSite and
   same-origin routing/exact credentialed CORS, preserve the backend JWT secret,
   and use normal PT15M/P30D lifetimes. Never publish secrets as NEXT_PUBLIC values.
6. Perform approved production startup/authentication/ownership/financial smoke
   checks. Existing tabs should reload; legacy localStorage JWTs do not establish
   refresh sessions and may require one fresh login. Resume service/cron only
   after the release checks and separate approval.

Rollback must preserve migration history: keep maintenance active and prefer a
corrected V2-aware build. A matching old build/database recovery requires explicit
operator approval. Do not delete the V2 table/history, repair, clean or rebaseline
to force an incompatible rollback. No role-fencing infrastructure is introduced.

Implementation/files/configuration details are in
[the Phase 1 report](authentication-phase-1.md); the optional browser reference
and private local evidence boundaries are in
[the local integration record](authentication-local-testing.md).
The [production preparation guide](authentication-production-deployment.md)
records the reviewed V2 commands, same-origin Vercel configuration, protected
deployment order, backup fallback and approval boundaries. The separately approved
production V2 execution is recorded in the release checkpoint linked above.
