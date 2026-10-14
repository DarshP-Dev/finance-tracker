# Flyway V1 milestone: completed adoption and deployment

Local PostgreSQL, the isolated Neon rehearsal, and Neon production have adopted
explicit Flyway **BASELINE 1**. The corrected backend deployed successfully at
`01c8de7`, including the shared validation fix from `fa44276`. The operator then
tested the live website and confirmed that its core functionality works.

This report is the final public record. Target identifiers, connection details,
credentials, backups, database captures, row digests, account information, and
raw inspection logs remain in Git-ignored local storage. No production action
was performed while finalizing this documentation.

## Completed stages and root causes

| Stage | Finding and resolution |
| --- | --- |
| Local inspection | Two legacy category checks omitted FOOD, RENT, and HEALTH; two investment positivity checks were absent. Separately reviewed local reconciliation resolved all four differences without changing records. |
| Neon rehearsal | Independent complete comparison found 145 matching signatures, exactly two missing investment checks, and no unexpected objects. Existing category checks already supported all current values. Atomic two-check reconciliation brought the schema to 147 matches. |
| Recovery verification | A pre-adoption production snapshot was restored to a new isolated branch without replacing production. All nine table counts/full-row hashes and eight sequence states matched the production reference. Supplemental schema/permission comparison found no unexpected anomalies. |
| Production preflight | Independent inspection confirmed the same two missing investment checks, no other schema differences, and all 87 canonical data rules passing. A fresh preservation comparison matched the retained, restore-tested recovery point; its reuse was explicitly approved. |
| Production reconciliation | Only the shares and purchase-price positivity checks were added and validated, in one guarded atomic transaction. All 147 signatures matched afterward; records and sequences were preserved. |
| Explicit adoption | One successful version-1 BASELINE history entry was registered. V1 schema-creation SQL was deliberately skipped. Strict validation and preservation checks passed. |
| Compatible deployment | Corrected source built and started on Vercel; Flyway, Hibernate, database connection, and Spring Boot startup passed. The operator subsequently confirmed live core functionality. |

The investment discrepancies were missing database enforcement of existing JPA
rules, not missing application calculations. The initial V1 SQL and its manifest
were correct and were never changed to conceal drift. Historical DDL provenance
was unavailable; the audit did not prove when those checks became absent.

## Baseline validation correction

The earlier deployment at `7f9113c` rejected V1 `BASELINE_IGNORED` using
`!state.isApplied()`, even after successful Flyway validation. That is a legitimate
state after explicit baseline adoption, not evidence that V1 should be executed.

`DatabaseMigrationTool.validateHistory` now serves both CLI validation and
validation-only startup. It accepts ignored V1 only with genuine successful
BASELINE 1 history and the matching Flyway baseline information. Missing,
incorrect, or failed baseline history; unsupported versions; pending/missing/
failed migrations; invalid checksums; and unexpected ignored states still fail.
See [the detailed fix report](flyway-baseline-validation-fix.md).

The finalization commit changes documentation only. Backend source, committed
tests, V1 SQL, schema manifest, Dockerfile, Vercel configuration, and database
history are not modified by this milestone cleanup. The earlier runtime fix and
startup regression are already preserved in Git.

## Verified production state at the preservation checkpoint

The final controlled-deployment read-only postflight completed on
2026-10-14 at 02:54:37 UTC, before subsequent normal live-application testing:

- 147 matching schema signatures; zero missing or unexpected signatures.
- All 87 canonical data compatibility rules passed; zero violations.
- Both investment positivity checks existed and were validated.
- All nine table counts/full-row hashes were unchanged from the post-baseline
  reference; all eight sequence last values and `is_called` flags were unchanged.
- Exactly one successful BASELINE 1 history entry, zero SQL migration entries,
  and zero unexpected history entries.
- Migration information contained only version-1 `BASELINE` and the V1 SQL
  `BASELINE_IGNORED` entry; strict validation passed.

These are dated preservation results, not a claim that financial records or
provider caches cannot change during normal application use. No new production
query, migration, reconciliation, baseline, repair, or clean was run to finalize
this record. The operator's later live check is operator evidence, not an
additional automated CRUD test.

## Test results and provenance

| Verification stage | Passing tests |
| --- | ---: |
| Initial Flyway implementation, full disposable backend suite | 475 |
| Local reconciliation regression additions, full suite | 500 |
| Shared baseline-validation correction, full suite | 514 |
| Committed repeated full-startup regression added | 1 additional case |
| Combined workspace suite including private operator-preparation tests | 555 |
| Focused baseline/configuration/repeated-startup verification before release | 20 |

The totals describe successive runs; they are not additive. The committed startup
case starts the full backend twice with database-enforced read-only connections,
schedulers/providers disabled, and verifies preserved schema, history, records,
and sequence state. Existing regressions cover baseline rejection, checksum and
pending-migration failures, transactional rollback, ownership, and financial flows.

Finalization's full run of the published test set: **515 passed, zero failures,
errors, or skips**, on a new guarded disposable PostgreSQL cluster. The cluster
shut down successfully; no existing or remote database was used. The earlier
555-test workspace result included 40 private operator-preparation cases.
Operator-specific preparation tests and the unused role-fencing experiment are
retained privately with their original scripts. They are excluded from publication
because they depend on private target pins or an approach that was not used.
Their historical results do not increase the published suite's current count.

## Operations after V1

Keep production startup in `DB_MIGRATION_MODE=validate`, Hibernate `validate`,
SQL initialization `never`, automatic baseline disabled, and Flyway clean disabled.
Runtime validation does not apply future migrations. Existing production must not
be reconciled or baselined again. New reviewed schema changes start at V2 and are
applied once through a separately approved release procedure before compatible
code is deployed.

The controlled deployment used All Deployments authentication, disabled Vercel
Cron Jobs, and `RECURRING_SCHEDULER_ENABLED=false`; it was re-paused after checks.
Those are recorded execution-time controls, not an assertion about settings
after later operator resumption. This documentation finalization changes none of
them. A push may trigger another Vercel build of the unchanged backend.

Keep the verified recovery point and private evidence usable. Review backup
freshness against subsequent writes before relying on it for a future recovery.
Use the [recovery checklist](neon-production-recovery-checklist.md) and
[database operations guide](database-migrations.md); approvals for future writes,
restores, resumption, and recurring processing are separate operational decisions.

Private preparation drafts were retained intact rather than published with stale
approval gates or infrastructure identifiers. The simplified beta procedure reused
the existing secure login and successful recovery verification; no new
administrative account or database-role fence was introduced.
