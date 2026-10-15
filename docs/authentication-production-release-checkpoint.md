# Authentication Phase 1 — production V2 and application release checkpoint

Recorded October 14, 2026 (America/Toronto). Production Flyway V2 adoption is
complete. Publishing the reviewed source to Git is approved; unpausing Vercel,
deploying/promoting the application and restoring public access or cron are
separate approval stages. No production data operation is part of Git publication.

## Completed database operation

The separately approved operator execution used the pinned, reviewed artifact
with exactly V1 and V2 resources. Read-only preflight confirmed the production
identity, the available fresh manual snapshot, maintenance controls, canonical
V1 schema, successful BASELINE 1 and only V2 pending. No competing database
sessions were present. Credentials, endpoint identifiers, captures, hashes,
sequence values, screenshots and raw logs remain private under ignored `.local/`.

- Exactly one SQL migration, V2, was applied successfully. BASELINE 1 remained
  unchanged; V1 schema-creation SQL was not executed or edited.
- Strict shared CLI/startup history and checksum validation passed, with no
  pending, failed, missing or unexpected migrations.
- All 168 application schema signatures match: 147 preserved V1 signatures and
  21 V2 additions. Refresh sessions have eight columns, seven validated
  constraints, five valid indexes and zero rows at the preservation checkpoint.
- All nine original table counts/full-row hashes and all eight sequence states
  matched the maintenance freeze. No financial records changed.
- Migration and validation logs contained no warnings or errors. No repair,
  clean, repeated baseline, reconciliation or financial DML was run.

The fresh snapshot was confirmed available with its restore action enabled and
expiration Never. That specific snapshot was not restore-tested; the operator
explicitly accepted this limited beta risk using the previously verified recovery
workflow. Console listed only the fresh snapshot; do not assume the earlier
snapshot is still available. No snapshot restore or switch-over was performed.

## Reviewed application and configuration

The tested backend/frontend and migration bytes were unchanged during production
adoption. The verified local baseline remains 540 backend tests and 17 frontend
tests passing, plus clean frontend lint/build. No full-suite rerun is needed for
documentation updates alone. Existing reports distinguish operator-confirmed
manual tests from tests not individually reported.

Spring Boot uses validation-only Flyway and Hibernate. Remote startup migration
mode is rejected. Preserve the existing database/JWT secrets; V2 introduces no
new external provider or credential. The repository's Vercel services deploy
Next.js/React and the backend together, with relative same-origin `/api` routing.

Production configuration was checked privately: `DB_MIGRATION_MODE=validate`,
`RECURRING_SCHEDULER_ENABLED=false`, explicit Secure refresh cookies, SameSite=Lax
and the exact canonical HTTPS CORS origin. The three new nonsecret cookie/CORS
settings were approved, saved to Production only and verified. Normal new token
lifetimes are PT15M/P30D from the reviewed code; there is no Production frontend
API-base override or linked shared variable. HTTPS cookie/CSRF behavior still
requires the protected smoke test against the newly deployed application.

`DB_DDL_AUTO` is obsolete for this runtime. Do not replace the hardcoded Hibernate
validate setting or introduce higher-precedence unsafe overrides. Vercel cron
and Spring scheduling remain distinct controls; leave both disabled for rollout.

## Protected rollout requiring separate approval

The project remains paused, Vercel Cron Jobs disabled and All Deployments Vercel
Authentication active. Anonymous requests to the canonical/current deployment
aliases redirected to Vercel authentication before pausing. A paused 503 masks
that gate, so saved settings and the prior check must not be confused with a
new anonymous challenge test while paused.

Automatic production-domain assignment is disabled. This was observed and left
unchanged. Neither a successful Git push nor a Ready deployment proves that the
canonical domain uses the reviewed application commit. A paused project has
previously blocked new builds; observe the actual post-push result rather than
assuming it will build or retry automatically.

If a paused build is blocked, the proposed separately approved sequence is:

1. Reconfirm All Deployments protection, no relevant exceptions/bypasses, cron
   off, Spring scheduler false and validation-only settings. Keep the source
   commit fixed and verify Production-scoped configuration is selected.
2. Temporarily resume the protected project, immediately check anonymous access
   still reaches Vercel authentication, and deploy/redeploy that exact commit.
   Avoid opening the old application: the V1-only backend can reject V2 history.
3. Require both services to build successfully and the backend to complete
   database connection, strict BASELINE 1/V2 validation and Hibernate validation.
   Inspect the Git SHA, Ready state and startup logs; do not rely on frontend
   readiness alone. Stop and re-pause on an unexpected failure.
4. With explicit promotion approval, promote that same deployment to Production.
   Confirm the canonical domain and backend/frontend services use its SHA.
   Keep All Deployments protection on and cron off throughout.
5. Run only the approved smoke checks from the
   [deployment guide](authentication-production-deployment.md). Login, refresh,
   logout and registration intentionally write session/account state, so include
   them explicitly in the smoke-test approval. Do not shorten Production TTLs.
6. Pause again after protected verification unless separately approved otherwise.
   Restoring public access and recurring processing requires separate approval;
   do not enable the Spring scheduler alongside Vercel cron.

No step above is authorized merely by committing this document. If protection
cannot be established or a deployment fails validation, stop rather than expose
the incompatible old application. No migration needs to be reapplied to activate
the release. Prefer a V2-aware forward fix for application failures; reverting
only to a V1-only backend is unsafe. Database recovery needs separate approval.

Existing beta users may need to reload old tabs and sign in once. Legacy browser
storage does not create refresh sessions. Logout revokes refresh sessions while
already-issued access JWTs remain valid until their own expiry. These documented
limitations and the known recovery risk remain unchanged by Git publication.
