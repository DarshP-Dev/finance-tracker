# Authentication Phase 1 — completed production release

**Complete as of October 15, 2026 (America/Toronto).** Implementation, local
verification, production Flyway V2 adoption, deployment and promotion are complete.
The operator confirmed the live website passes manual authentication testing and
existing financial features remain functional. Authentication Phase 2 is the next
development milestone; it has not been started.

## Implementation and verification

The complete backend, frontend, Flyway V2, regression tests and original reports
are included in commit `c9e5677c9e9a6f752d568b0c2dfa3856c8ab9801`.

Implemented behavior includes hashed opaque refresh-token storage, absolute
session expiry, refresh rotation and replay-family revocation, memory-only access
JWTs, automatic renewal, session restoration, CSRF protection, secure cookies,
refresh-session logout and cross-tab logout. Production uses same-origin
`/api` routing, Secure/HttpOnly/SameSite=Lax refresh cookies and exact-origin CORS.

The existing verified baseline is **540 backend tests and 17 frontend tests
passing**, plus clean frontend lint, TypeScript checks and production build.
Documentation finalization reuses those results; no application code change or
unresolved reported authentication failure requires a repeated full-suite run.
The [local review](authentication-phase-1-final-review.md) retains its dated test
inventory and distinguishes reported browser results from unreported scenarios.

## Completed production database adoption

The approved operator execution used a pinned artifact containing exactly V1 and
V2. Preflight confirmed the intended production database, a fresh available manual
snapshot, canonical V1 schema, successful BASELINE 1 and only V2 pending.

- V2 was applied once successfully; BASELINE 1 remained unchanged. V1 SQL was not
  executed or edited.
- Strict history/checksum validation passed with no pending, failed, missing or
  unexpected migrations.
- All **168 application schema signatures** matched: 147 preserved V1 signatures
  and 21 V2 additions. Refresh sessions had eight columns, seven validated
  constraints, five valid indexes and zero rows at the preservation checkpoint.
- All nine existing table counts/full-row hashes and all eight sequence states
  matched the maintenance freeze. No financial records changed during adoption.
- No repair, clean, repeated baseline, reconciliation or financial DML was run.

The fresh pre-authentication snapshot was confirmed available and displayed as
never expiring. That particular snapshot was not restore-tested; the operator
accepted this limited beta risk using the previously verified recovery workflow.
This is historical backup evidence, not a claim about current retention or a
new recovery rehearsal. No snapshot restore or switch-over was performed.

## Deployment and promotion outcome

Both services were built from the verified `c9e5677` source and staged together
with Vercel CLI 64.1.0 using `--prod --skip-domain`.

- Deployment: `dpl_ERoZPTQFrzq4rRJg7nDHgNQMqcsq`.
- Frontend compilation and backend build passed. Spring Boot started, connected
  to PostgreSQL, completed Flyway validation and initialized Hibernate/JPA.
- Startup logs contained no runtime errors or migration execution. Build notices
  concerned npm configuration/install scripts and existing unchecked test code.
- Read-only postflight again matched all 168 signatures, nine existing table
  fingerprints, eight sequence states and unchanged BASELINE 1/SQL V2 history.
- With separate approval, the existing Ready deployment was promoted to
  **Current for Production while the project remained paused**. No rebuild,
  redeploy or temporary resume was needed for promotion.

The canonical production domain and generated project alias resolved to this
corrected deployment after promotion. The git-main branch alias remained on the
previous deployment at that check and was intentionally left unchanged. Use the
canonical hostname for beta testing. A later Git push can update branch aliases;
inspect their actual assignments rather than assuming all generated URLs match.

The earlier alias stop was explained by Vercel metadata: custom-domain assignment
was disabled, while `automaticAliases` still included the generated CLI project
URL. That URL was not a separately configured production domain. See Vercel's
[generated URL reference](https://vercel.com/docs/deployments/generated-urls) and
[staged production promotion procedure](https://vercel.com/docs/deployments/promoting-a-deployment).
No individual alias repair was necessary to promote the canonical application.

During the controlled rollout, All Deployments protection remained enabled,
Vercel cron was off and `RECURRING_SCHEDULER_ENABLED=false`. Flyway and Hibernate
remained validation-only. These are dated rollout observations; they do not
assert the site's present pause/protection/cron switches after operator testing.
Milestone documentation does not change those operational settings.

## Live manual testing and evidence boundaries

After promotion, the operator explicitly reported that Authentication Phase 1 is
complete and working on the live Vercel website, manual production authentication
tests passed and existing financial features remain functional. Reported working
flows include secure refresh sessions/rotation, automatic access renewal, reload
persistence, logout and cross-tab logout.

These are operator-confirmed live results, separate from the agent-observed
build, startup, schema and preservation evidence. Registration, password changes,
offline logout recovery and every individual cookie attribute were not separately
itemized in the final production report; do not imply an exhaustive independent
browser audit. No authentication bug remains reported.

Normal approved login/refresh/logout operations legitimately change session
records. Preservation hashes describe the frozen migration/deployment checkpoint,
not an assertion that live user activity leaves the database permanently unchanged.

## Known limitations and proposed Authentication Phase 2 follow-ups

1. **Access-token revocation:** logout/password changes revoke refresh families;
   already-issued access JWTs remain valid until their own expiry (normally
   15 minutes). Evaluate whether immediate revocation is needed before adding a
   denylist or per-request session checks.
2. **Concurrent/lost refresh responses:** Web Locks serialize supported browser
   tabs. Unsupported browsers, rotation response loss or retries can require a
   fresh sign-in under strict reuse detection. Add focused browser/network cases
   before considering any change to replay handling.
3. **Offline logout:** server revocation cannot be guaranteed without connectivity;
   the UI reports failure and offers Retry logout. Expand automated/browser
   coverage for outage recovery.
4. **Abuse controls:** add scoped authentication rate limiting and review generic
   failure messages and sanitized security logging. HttpOnly cookies and
   memory-only JWTs do not eliminate XSS risk.
5. **Session lifecycle:** design bounded cleanup after absolute family expiry,
   retaining consumed-token evidence until replay detection no longer needs it.
   Consider session visibility and per-session revocation if included in Phase 2.
6. **Additional scenarios:** explicitly cover registration, password-change
   revocation, denied access and browser compatibility when defining the next
   phase. Keep its scope separate from this completed release.

Existing beta users may need one reload/fresh login after upgrading older tabs;
legacy localStorage tokens do not establish refresh sessions. Keep normal
PT15M/P30D lifetimes in production and restore normal local lifetimes after
short-expiry testing. Local `start-dev.ps1` still defaults to migrate; explicitly
select validate during ordinary development when automatic adoption is unwanted.

No new feature, scheduler, provider, schema or behavior change is part of this
milestone closure. Future database operations need a fresh preflight and their own
approval. Do not repeat V2 or delete migration/session history to roll back.
Prefer a compatible V2-aware forward fix; database restore and a matching older
application would require explicit recovery approval.

## Git and private evidence

Final milestone publication is documentation-only. Implementation source, tests,
Vercel configuration and V1/V2 migration bytes remain unchanged from `c9e5677`.
A documentation push may create another Vercel deployment; Ready alone does not
prove promotion or canonical-domain assignment. Observe the Git SHA and aliases,
and do not manually promote, redeploy or change settings during finalization.

Credentials, cookies, JWTs, CSRF values, provider keys, private database captures,
hashes, sequence values, backups and raw logs stay outside Git in ignored local
storage. This public report contains no financial records or private database
connection details.
