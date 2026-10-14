# Completed Flyway-compatible backend deployment

The corrected backend at `01c8de7`, including `fa44276`'s shared baseline-validation
fix, was built and deployed successfully to Vercel. Production domains were
associated with that corrected artifact, replacing the baseline-incompatible
`7f9113c` artifact. The operator subsequently confirmed live core functionality.
Deployment identifiers, domains, connection details, and raw logs stay private.

## Protected execution and startup

The initial Git-triggered deployment was blocked because the project was paused;
no build or startup occurred. The operator separately approved a temporary
protected resumption and redeployment of the corrected commit.

All Deployments authentication was saved and verified after reload without an
upgrade. The pre-resumption anonymous root probe returned `503 DEPLOYMENT_PAUSED`;
the pause masked the authentication challenge. Immediately after approved resume,
the anonymous request redirected to Vercel authentication. It sent no cookies,
authorization, or protection-bypass headers. Public application access remained
protected. Cron remained disabled throughout, and the new deployment used latest
Production settings, including `RECURRING_SCHEDULER_ENABLED=false` and
`DB_MIGRATION_MODE=validate`.

The Java 21 backend container built successfully. Runtime logs on
2026-10-14 at approximately 02:49 UTC confirmed:

- Successful Hikari connection to the expected production database, PostgreSQL 17.11.
- Flyway successfully validated two migration-information entries. This means
  the BASELINE marker and ignored V1 SQL, not two executed SQL migrations.
- Hibernate initialized its JPA EntityManagerFactory with validation-only configuration.
- Tomcat started; Spring Boot completed startup in 21.2 seconds.
- No application-run failure, baseline rejection, or Hibernate validation failure.

GET `/api/transactions` without an application JWT returned the expected Spring
Security `403`. The browser's initial timeout/error-page presentation was not a
backend startup failure. No authenticated financial mutation or manual recurring
processing was invoked for deployment verification.

The project was paused after startup checks. An unintended extra Resume occurred
during the final UI check and was immediately corrected by re-pausing. All
Deployments authentication remained enabled; no financial API operation was
invoked during that interval. A fresh preservation capture after the final pause
confirmed no data or sequence changes. This incident is part of the execution
record, not omitted from the milestone history.

## Final controlled-deployment verification

The final anonymous production response was `503 DEPLOYMENT_PAUSED`. Read-only
postflight verified 147 V1 schema signatures, all 87 data rules, unchanged nine-table
counts/hashes and eight sequence states, and exactly one successful BASELINE 1
with zero SQL migration entries. Strict validation passed with only BASELINE and
V1 BASELINE_IGNORED states. No new migration or reconciliation was run.

One idle JDBC connection remained, with no transaction or write locks. Its
connection timestamps matched backend startup, supporting an application-session
inference rather than independently proving identity. It was not terminated.
Project pause blocks HTTP traffic; it does not certify container termination.

These results apply to the controlled verification window. Later website use was
tested successfully by the operator; no new production activity or Vercel setting
change is part of Git milestone finalization. See the
[completed adoption report](flyway-production-adoption-report.md) for provenance
and [recovery checklist](neon-production-recovery-checklist.md) for future operations.
