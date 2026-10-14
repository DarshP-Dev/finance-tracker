# Local schema reconciliation: investigation and reviewed adoption procedure

**Final status:** local reconciliation and explicit BASELINE 1 adoption succeeded.
The isolated Neon rehearsal and production also completed their independently
reviewed two-investment-constraint reconciliation and adoption. All preservation
checks passed; the corrected production backend works. See the
[completed milestone](flyway-production-adoption-report.md).

This document retains the **historical pre-adoption investigation and preparation
report**. Its original unresolved-difference statements and approval gates are
dated evidence, not the current database state. The local-only scripts remain
guarded examples for a separately reviewed unadopted local database. Do not rerun
them or baseline on an already-adopted database, and do not use them against Neon.

## Historical local investigation

Prepared October 12, 2026 for local PostgreSQL **17.9**, database
`finance_tracker_db`. **Preparation and disposable testing only. The real local
database has not been reconciled or baselined. Neon was not contacted.**

## Findings and root causes

Read-only catalog queries confirm these four differences from V1 and the current
JPA mappings:

| Constraint | Before, in the actual local database | After the prepared repair |
| --- | --- | --- |
| `budgets_category_check` | Validated CHECK allowing 16 legacy categories | Validated CHECK allowing all 19 current enum values |
| `transactions_category_check` | Validated CHECK allowing the same 16 legacy categories | Validated CHECK allowing all 19 current enum values |
| `ck_investments_shares_positive` | Absent | Validated `CHECK (shares > 0)` |
| `ck_investments_purchase_price_positive` | Absent | Validated `CHECK (purchase_price > 0)` |

The category checks match the enum in commit `b8b9d5c`. Commit `4a1647f` added
`FOOD`, `RENT`, and `HEALTH` to Java, but the two existing database checks retained
the older list. The recurring category check already has all 19 values. This is
schema drift from the earlier Hibernate-managed schema: existing CHECK
constraints were not synchronized with the expanded mappings.

Both positive investment checks are present in `Investment` from its introduction
in commit `70f2441`, and remain present today. Neither exists in the local catalog.
The precise discrepancy is missing database enforcement of existing mapped rules;
there is no corresponding missing Java rule. Git history and the current catalog
cannot establish whether original creation omitted them or later DDL removed
them: no historical database DDL audit was available. It would be inaccurate to
claim a particular historical removal or creation event was proven.

Normal transaction and budget API writes normalize `FOOD → DINING`,
`RENT → HOUSING`, and `HEALTH → HEALTHCARE` through the existing
`TransactionCategoryNormalizer`; therefore the old checks have not blocked those
normal API paths. They can reject direct persistence of valid enum aliases.
`InvestmentService`, request validation, and entity validation already reject
nonpositive quantities/prices. The missing SQL checks leave direct database
writes without that protection. No financial service or normalization change is
required, and no stored category needs renaming.

**V1 is correct and unchanged.** Its category lists and investment checks match
the current mappings. Changing V1 or the strict adoption manifest to match the
drift would conceal the missing constraints. The repair is outside Flyway's
classpath and is never run during application startup. No V2 repair migration
was added: the existing schema must match V1 before explicit baseline adoption.

## Existing-record checks

The actual local inspection used `BEGIN READ ONLY` and a connection-level
`default_transaction_read_only=on`, with explicit loopback database coordinates.
Schema capture was schema-only, with no rows, ownership or grants exported.

| Proposed rule | Violating local rows |
| --- | ---: |
| Budgets: category is nonnull and one of the current 19 values | 0 |
| Transactions: same category rule | 0 |
| Investments: nonnull `shares > 0` | 0 |
| Investments: nonnull `purchase_price > 0` | 0 |

No affected invalid records were found. This is a point-in-time observation;
the script rechecks under locks before any DDL. If a later preflight reports
violations, **stop**. Investigate identifiers and failed rules privately; do not
silently round, replace, delete or otherwise correct records. For example, this
read-only query identifies investment violations without exposing account details:

```sql
BEGIN READ ONLY;
SELECT id,
       (shares IS NULL OR NOT (shares > 0)) AS invalid_shares,
       (purchase_price IS NULL OR NOT (purchase_price > 0)) AS invalid_purchase_price
FROM public.investments
WHERE shares IS NULL OR purchase_price IS NULL
   OR NOT (shares > 0) OR NOT (purchase_price > 0)
ORDER BY id;
ROLLBACK;
```

## SQL prepared for separate review

Files under `backend/backend/database/reconciliation/`:

1. `00_read_only_preflight.sql`: database/version/history check, current
   definitions, violation counts and category aggregates. No schema/history writes.
2. `10_reconcile_local_constraints.sql`: the four changes in **one atomic
   transaction**, guarded by exact database-name confirmation and a deliberate
   approval session setting. It refuses nonloopback connections, existing Flyway
   history, unexpected category definitions, partial/already-applied repair and
   violating records. It locks the three affected tables before rechecking.
3. `20_read_only_postflight.sql`: prints four final definitions and requires four
   validated named checks. The complete Java `verify` check is still mandatory;
   this SQL alone is not proof of full schema equivalence.

The widened category checks are installed under temporary names, validated, then
the old checks are replaced using their original names. The old checks are kept
until validation succeeds. Both investment checks are added and validated. No
INSERT, UPDATE, DELETE, TRUNCATE, table drop, monetary type conversion, category
normalization or sequence reset occurs in the repair. PostgreSQL enforces new
writes with `NOT VALID` checks and verifies existing rows with `VALIDATE
CONSTRAINT`; all four final checks are validated before commit.
See [PostgreSQL 17 ALTER TABLE](https://www.postgresql.org/docs/17/sql-altertable.html).

Approval guards do not replace target/backup review or restrict a database owner's
other powers. They intentionally make accidental execution and reruns fail. The
expected legacy definitions are PostgreSQL 17 deparsing; a different server or
equivalent but differently represented schema must be reinspected, not bypassed.

## Additional adoption bug found during rehearsal

The original validation-only startup checked that every resolved migration was
`isApplied()`. After explicit baseline 1, Flyway correctly reports V1's SQL as
`BASELINE_IGNORED` alongside the applied `BASELINE` history row. That state failed
the original check even though baseline and Flyway validation succeeded.

`DatabaseMigrationConfiguration` now accepts only version 1 in
`BASELINE_IGNORED` when an actual `BASELINE` version 1 is present. Missing history,
unsupported baseline versions, checksum problems, pending V2 and other unapplied
states still fail. Startup still uses Hibernate `validate` and never automatically
baselines or repairs schema. This small compatibility fix is required for the
documented validation-only startup after local adoption; it makes no database
changes itself.

## Isolated verification

The tests reconstruct all nine tables, identity sequences, keys and indexes from
the schema-only local export, with **synthetic** records in every table, including
all 16 old category values and fractional money/share values. The fixture uses
equivalent `IN` syntax for enum checks: reparsing pg_dump's explicit array casts
otherwise changes the deparsed expressions. The strict verifier proves the
fixture has exactly the two changed checks and two missing checks (six catalog
signatures), with no unrelated differences.

The tests verify complete V1 manifest equivalence after repair, byte-equivalent
JSON representations of every synthetic table's records, all eight identity
sequence counters/`is_called` flags, preserved old categories, acceptance of the
three added categories, invalid categories/nonpositive writes rejected, invalid
existing investments rejected unchanged, approval/target/history/unknown-schema
guards, lock timeout, repeat refusal and rollback after a deliberately induced
failure **after the first table was repaired**.

A corrected populated disposable database is explicitly baselined at 1, skips V1,
starts Spring/Hibernate in validation-only mode and preserves data. A synthetic
V2 index migration is blocked while pending in validation-only mode, then applied
once by controlled migration and accepted afterward. This V2 exists only in a
temporary test directory, not application resources.

Run from `backend/backend`:

```powershell
.\run-tests.ps1 -Tests 'ReconciliationTests,ReconciledSchemaApplicationTests,FlywayMigrationTests,DatabaseMigrationConfigurationTests'
.\run-tests.ps1
```

Final full suite: **500 tests passed, zero failures/errors/skips**, preserving the
475-test baseline and adding **25 reconciliation cases** (24 SQL/adoption cases
and one Spring/Hibernate validation integration case). The isolated `psql` smoke
rehearsal also passed the documented `-c` approval settings plus `-f` script
sequence: all four checks validated, synthetic investment amounts unchanged and
no Flyway history created by reconciliation. The disposable clusters were stopped.

Final read-only schema exports before/after this work are identical after removing
pg_dump's random `restrict`/`unrestrict` tokens. The real local database still has
no Flyway history and all four violation counts remain zero. V1 and its catalog
manifest have no diff. Tests always create a new loopback PostgreSQL cluster on a
non-5432 port with unique `ft_test_` databases; they cannot target the protected
local or remote database. Private logs and schema exports are ignored under
`backend/backend/.local/`.

Reviewable files created: the three SQL scripts, `ReconciliationTests.java`,
`ReconciliationTestDatabase.java`, `ReconciledSchemaApplicationTests.java`,
`src/test/resources/db/legacy/local-pre-flyway-schema.sql` and this guide. Modified:
`DatabaseMigrationConfiguration.java` for the baseline-state compatibility fix,
and the two original Flyway docs to link this follow-up. No other application
logic, entity, V1, manifest or frontend file changed.

## Exact manual local commands: only after your review and approval

**None of the write commands below was run against an existing database.**
Stop the backend, recurring schedulers/cron and other schema/data writers. Obtain
a current full backup and prove restoration into a separate disposable target
before proceeding. A schema-only export or `pg_restore --list` is not a restore
test. Retain backup timestamp and preflight results privately. This task has not
created or tested a backup of real financial data.

Open PowerShell and define this local-only helper. It reuses the existing
Windows-encrypted database password without putting it in source or arguments:

```powershell
Set-Location '<repository-root>/backend/backend'
$env:DB_URL = '<configured local JDBC URL>'
$env:DB_USERNAME = 'postgres'
. .\dev-environment.ps1

function Invoke-ReviewedLocalConstraintSql {
    param([string]$Script, [switch]$ApprovedRepair)
    Invoke-WithDevEnvironment -InitializeJwt $false -Action {
        $savedPgPassword = $env:PGPASSWORD
        $savedPgOptions = $env:PGOPTIONS
        try {
            $env:PGPASSWORD = $env:DB_PASSWORD
            $env:PGOPTIONS = '-c default_transaction_read_only=on'
            $psqlArgs = @('-X', '-w', '-h', '127.0.0.1', '-p', '5432',
                '-U', 'postgres', '-d', 'finance_tracker_db', '-v', 'ON_ERROR_STOP=1')
            if ($ApprovedRepair) {
                $env:PGOPTIONS = $null
                $psqlArgs += @('-c', "SET finance_tracker.reconciliation_approved = 'approved-local-four-constraints'; SET finance_tracker.reconciliation_database = 'finance_tracker_db';")
            }
            $psqlArgs += @('-f', $Script)
            & 'C:\Program Files\PostgreSQL\17\bin\psql.exe' @psqlArgs
            if ($LASTEXITCODE -ne 0) { throw 'SQL failed. Stop; do not baseline.' }
        } finally {
            $env:PGPASSWORD = $savedPgPassword
            $env:PGOPTIONS = $savedPgOptions
        }
    }
}

# Read-only checks; all violation counts must be zero and history absent.
Invoke-ReviewedLocalConstraintSql -Script '.\database\reconciliation\00_read_only_preflight.sql'
.\database.ps1 -Command verify
# Before repair, verify must fail with precisely the four known differences.
```

Review the SQL and the exact before/after states above. Only after explicit
approval, verified backup restoration and a maintenance window:

```powershell
# WRITES FOUR CONSTRAINTS. The script rolls back on any failure.
Invoke-ReviewedLocalConstraintSql -Script '.\database\reconciliation\10_reconcile_local_constraints.sql' -ApprovedRepair

# Must both succeed before adoption. Stop on any error or additional difference.
Invoke-ReviewedLocalConstraintSql -Script '.\database\reconciliation\20_read_only_postflight.sql'
.\database.ps1 -Command verify

# SEPARATE APPROVED HISTORY WRITE, only after successful full verification.
.\database.ps1 -Command baseline -ConfirmDatabase finance_tracker_db -BackupVerified
.\database.ps1 -Command validate
.\database.ps1 -Command migrate

# Validation-only startup after controlled adoption/migration.
$env:DB_MIGRATION_MODE = 'validate'
.\start-dev.ps1
```

Compare pre/post record counts, identifiers, categories and financial amounts
privately against the backup. History must contain version 1/type `BASELINE`,
success true and no executed V1 SQL row. Confirm login, transactions, budgets,
investments, analytics and recurring workflows, then restart and check no repeat
DDL. Re-enable writers only after these checks. Use no `repair`, `clean`, automatic
baseline or Hibernate `update` to hide failures.

## Remaining risks and scope

The real local database still has its original four discrepancies and no Flyway
history. Approval is needed to **execute** the prepared repair and then separately
baseline. Current zero violations do not guarantee future data; locked rechecks
handle that. ACCESS EXCLUSIVE locks block readers/writers during the repair;
5-second lock and 60-second statement limits cause safe failure if the maintenance
window is unsuitable. Stop concurrent DDL/operators as well as application writers.

Catalog equivalence, Hibernate validation and full regressions were rehearsed
with synthetic data, not a copy of actual finances. Owners/grants and objects
outside `public` are not certified by the adoption verifier. Real backup recovery
and those permissions remain operator prerequisites. This repair is specifically
for the observed local schema; do not use it on Neon. No production inspection,
commit, push or deployment occurred.
