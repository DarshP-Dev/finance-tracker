# Flyway implementation report

Follow-up, October 12, 2026: the four local differences now have separately
reviewable SQL and a successful disposable rehearsal in
[Local schema reconciliation](local-schema-reconciliation.md). The full suite now
passes **500 tests**. That follow-up also fixes validation-only startup accepting
V1 after an explicit baseline. The actual local database remains unchanged and
unbaselined. The original implementation report below is retained as historical
context; its 475-test result predates this follow-up.

## A. Implementation summary

Implemented safe schema ownership using Spring Boot 4.0.6's Flyway starter and
Boot-managed Flyway 11.14.1 PostgreSQL support. Hibernate uses `validate` and
never `update`. Default startup checks history/checksums without migrating.
Local startup may explicitly migrate an empty/adopted schema; populated schemas
without history fail. Adoption is a separate verified operator action, never
automatic. No actual local or Neon database was baselined or migrated.

## B. Root schema assessment

The actual local database is PostgreSQL 17.9. Read-only schema export/catalog
verification identified nine tables, eight identity sequences, seven user FKs,
four unique constraints and ten application indexes. Financial precision,
timestamps, nullable EOD provenance and notification cascade/deduplication match
existing mappings. `ai_insights` remains a persistent mapped legacy table.
No application functions/triggers/views appeared in the dump. Standard internal
FK triggers and `plpgsql` are expected.

Four constraint differences prevent adoption of the actual local database:

* `budgets_category_check` and `transactions_category_check` omit mapped
  `FOOD`, `RENT`, `HEALTH` values.
* `ck_investments_shares_positive` and
  `ck_investments_purchase_price_positive` are absent.

Read-only inspection found **zero** nonpositive investment rows. This is useful
preflight evidence, not approval to add constraints. The canonical schema
reflects current mappings; no reconciliation was silently performed. Existing
schema drift must receive a separate review/approval before adoption. Hibernate
validation alone does not verify all these constraints. Neon was not contacted;
production schema equivalence is unknown.

## C. Migration files

One migration: `V1__initial_schema.sql`. On an empty `public` schema it creates
`users`, `ai_insights`, `budgets`, `investments`, `market_quote_snapshots`,
`notification_receipts`, `notifications`, `recurring_transactions`, and
`transactions`, with their identity generators, primary/foreign/unique keys,
enum and investment checks, and indexes. It uses current monetary precision and
date/timestamp conventions. No inserts, updates, drops, truncation or
reconciliation statements occur in V1. Existing ON DELETE CASCADE relationships
for notification tables are retained. Future changes start at V2; applied files
and the adoption manifest must not be edited.

## D. Flyway configuration

Flyway is enabled, initialized before JPA, checksum/file-name validation enabled,
`clean-disabled=true`, `baseline-on-migrate=false`, baseline version 1,
out-of-order disabled, no migration ignore patterns, and no automatic schema
creation. SQL initialization is disabled. Unsupported baseline versions and
attempts to enable Hibernate schema writes/automatic baseline/clean fail.

* Default/production: `DB_MIGRATION_MODE=validate`. Missing history or unapplied
  migrations prevents startup. Apply controlled migrations before deploying.
* Local: `start-dev.ps1` defaults to `migrate` if no environment override is set.
  It rejects remote DB_URL / SPRING_DATASOURCE_URL values and obsolete
  DB_DDL_AUTO=update. Existing password/JWT loading was extracted unchanged into
  a shared helper; provider credentials remain environment-managed.
* Tests: Surefire activates a guarded test profile. `TEST_DB_*` points at a new
  loopback cluster on a non-5432 port with a unique `ft_test_` database. The runner
  clears provider keys, disables schedulers/providers by default, restores
  environment values afterward and stops only its own disposable instance.
  Existing integration mocks may explicitly enable synthetic providers.

## E. Baseline strategy

Fresh databases execute V1 normally. Existing databases first pass a strict,
read-only catalog comparison with the immutable V1 manifest. It compares columns,
types/nullability/defaults, keys/checks, index definitions/validity, sequence
definitions/ownership, relations/RLS/policies, functions, triggers, enum/domain
types, rules and extensions. Unknown or incompatible objects reject adoption.
Owners/grants, physical column order, current sequence counters and objects
outside public require manual review rather than being changed.

Explicit baseline requires exact database-name confirmation and verified-backup
acknowledgement. It locks application tables, rechecks compatibility and registers
Flyway BASELINE version 1 through the standalone tool. It does not execute V1 or
modify financial rows/identifiers. Tests compare every application's table data
before/after adoption and confirm identity generation and sequence preservation.
Repeated baseline on existing history and incorrect baseline versions fail.

## F. Changed files

Modified:

* [README.md](../README.md)
* [pom.xml](../backend/backend/pom.xml)
* [application.properties](../backend/backend/src/main/resources/application.properties)
* [start-dev.ps1](../backend/backend/start-dev.ps1)

Created:

* [database.ps1](../backend/backend/database.ps1)
* [dev-environment.ps1](../backend/backend/dev-environment.ps1)
* [run-tests.ps1](../backend/backend/run-tests.ps1)
* [DatabaseMigrationConfiguration.java](../backend/backend/src/main/java/com/financetracker/backend/config/DatabaseMigrationConfiguration.java)
* [BaselineSchemaVerifier.java](../backend/backend/src/main/java/com/financetracker/backend/migration/BaselineSchemaVerifier.java)
* [DatabaseMigrationTool.java](../backend/backend/src/main/java/com/financetracker/backend/migration/DatabaseMigrationTool.java)
* [V1__initial_schema.sql](../backend/backend/src/main/resources/db/migration/V1__initial_schema.sql)
* [schema-inventory.sql](../backend/backend/src/main/resources/db/baseline/schema-inventory.sql)
* [V1__schema_manifest.txt](../backend/backend/src/main/resources/db/baseline/V1__schema_manifest.txt)
* [DatabaseMigrationConfigurationTests.java](../backend/backend/src/test/java/com/financetracker/backend/config/DatabaseMigrationConfigurationTests.java)
* [DisposableDatabaseGuard.java](../backend/backend/src/test/java/com/financetracker/backend/migration/DisposableDatabaseGuard.java)
* [FlywayMigrationTests.java](../backend/backend/src/test/java/com/financetracker/backend/migration/FlywayMigrationTests.java)
* [FlywayApplicationIntegrationTests.java](../backend/backend/src/test/java/com/financetracker/backend/migration/FlywayApplicationIntegrationTests.java)
* [application-test.properties](../backend/backend/src/test/resources/application-test.properties)
* [spring.factories](../backend/backend/src/test/resources/META-INF/spring.factories)
* [database-migrations.md](database-migrations.md)
* This report.

No frontend, entity, financial-service calculation, JWT, Twelve Data caching or
notification behavior files were changed. Private `.local/` logs/test clusters,
schema dumps and credentials remain ignored, untracked and excluded from this list.

## G. Testing

Final full suite: **475 executed, 475 passed, 0 failures/errors, 0 skipped** on
isolated PostgreSQL 17.9. This preserves all **437 existing tests** and adds **38**:

* 31 migration cases: fresh schema/manifest/history; repeatability; populated
  explicit baseline and record preservation; no automatic adoption; 13 schema-drift
  cases; read-only verification; wrong target/history/version rejection; checksum
  and missing/future migration failures; V2 after baseline; transactional failure
  rollback; identity/FK/unique/dedup/snapshot integrity; protected-target guard;
  validation-only startup preservation, empty-history failure and pending-DDL refusal.
* 5 configuration tests: invalid mode, Hibernate writes, automatic baseline, clean
  enabled, and no history on production-mode startup.
* 2 running-application integration tests: Flyway before Hibernate validation and
  no-op repeated initialization; HTTP registration/login/JWT, transaction CRUD,
  budgets, recurring records, investments, dashboard/analytics/insights/notifications.

Existing financial, ownership, quote-snapshot, concurrency and notification tests
also passed against the migrated PostgreSQL schema. No external provider calls or
credits were required. Docker's daemon is unavailable; installed PostgreSQL tools
provided real-database coverage without introducing a Docker requirement.
PowerShell scripts parsed successfully; `git diff --check` passed. Changed-file
API/private-key pattern scan found zero matches. Read-only before/after schema
exports show the actual local schema is unchanged, with no Flyway history table.
The packaged `database.ps1 -Command verify` command also passed on the disposable
reference database; it started no Spring application and left migration history absent.

Production backup/recovery, production schema, Neon-branch rehearsal and actual
production deployment were intentionally not performed. They remain prerequisites.

## H. Local adoption instructions (do not run until prerequisites are met)

Stop writers/cron and verify recovery from the completed backup on a disposable
target. Review and obtain explicit approval to reconcile the four constraint
differences above. No reconciliation is included or authorized by this task.
Configure the target and run read-only verification:

```powershell
cd C:\Users\Darsh\FinanceTracker\backend\backend
$env:DB_URL = 'jdbc:postgresql://localhost:5432/finance_tracker_db'
$env:DB_USERNAME = 'postgres'
.\database.ps1 -Command verify
```

This currently fails safely with the reported differences. Once separately
approved reconciliation is complete, verification passes and backup/adoption are
explicitly approved, the manual sequence is:

```powershell
.\database.ps1 -Command baseline -ConfirmDatabase finance_tracker_db -BackupVerified
.\database.ps1 -Command validate
.\database.ps1 -Command migrate
.\start-dev.ps1
```

Inspect history (version 1 BASELINE), record identifiers/counts and application
behavior afterward; restart should apply nothing twice. For a fresh approved
empty database, use migrate without baseline. Do not bypass errors with clean,
repair, automatic baseline, disabled Flyway or Hibernate update. None of the
actual local adoption commands was executed during implementation.

## I. Production adoption instructions

Before any production connection/write, obtain approval; verify a usable Neon
recovery point and restore path. Inspect production read-only and compare to V1,
including custom schemas/privileges. Rehearse on a separate Neon development
branch with providers/cron disabled. Resolve differences through separate review.

Stop production writers/concurrent deployments. Use a direct TLS Neon connection
and controlled migration role, secure environment variables and exact target-name
confirmation. Run verify, explicit baseline 1, migrate, then validate through
`database.ps1 -AllowRemote` (with ConfirmDatabase/BackupVerified for baseline).
Full command examples and Linux Java invocation are in [the operations guide](database-migrations.md).

Deploy the compatible backend afterward; Vercel runtime uses pooled connections,
unchanged JWT/provider secrets, `DB_MIGRATION_MODE=validate` and no obsolete
DB_DDL_AUTO=update. Do not put adoption/migration in the container build or
repeated serverless startup. Use a separate branch for previews. Inspect history,
Hibernate compatibility, authentication/ownership and core workflows before
resuming cron/writes. Flyway locking does not replace deployment ordering.

On failure stop deployment and inspect transactional state. Flyway Community
does not automatically undo migrations. An application rollback does not restore
database state; old Hibernate update settings must not reappear. Prefer a reviewed
forward fix, or restore into a separate verified recovery target and switch
connections deliberately, accounting for writes since the backup.

## J. Remaining risks

Actual local adoption is blocked by four known constraints; their correction
requires separate approval, even though no nonpositive investment rows were found.
Production schema/backup/permissions and Vercel/Neon rollout still require review.
The strict manifest can reject equivalent constraint names or extension versions;
review such differences rather than bypassing verification. Grants/owners and
non-public objects need manual review. Maintenance adoption cannot run concurrently
with other operators changing schema. Keep applied migrations immutable.

## K. Final readiness assessment

The implementation and complete test suite are verified for fresh PostgreSQL and
explicit adoption of compatible disposable populated schemas. The actual local
database is **not ready for adoption** until the reported drift is separately
resolved and verification succeeds. Production readiness remains conditional on
approved backup/schema inspection and an isolated Neon rehearsal.

No existing database was modified by this work. No commit, push or deployment was
performed. Changes remain available for review.
