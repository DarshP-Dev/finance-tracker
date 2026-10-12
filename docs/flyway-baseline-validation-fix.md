# Flyway CLI validation after explicit baseline 1

## Root cause and read-only local findings

The local database `finance_tracker_db` on PostgreSQL 17.9 was already reconciled
and adopted by the operator. Read-only inspection found exactly one history row:

| Version | Type | Script | Success |
| --- | --- | --- | --- |
| 1 | BASELINE | `<< Flyway Baseline >>` | true |

Flyway's actual migration information contains exactly two entries:

| Version | Type | State |
| --- | --- | --- |
| 1 | SQL | BASELINE_IGNORED |
| 1 | BASELINE | BASELINE |

No migration is pending, missing, failed or unexpected. The two entries do not
mean two executed migrations: V1's SQL was deliberately skipped when the existing
schema was baselined. Flyway's own validation succeeds.

`DatabaseMigrationTool` still checked `!state.isApplied()` for every entry after
that validation. `BASELINE_IGNORED` is deliberately not applied, so the tool threw
`Migration history is not fully applied`. The earlier startup fix in
`DatabaseMigrationConfiguration` had not been shared with the CLI. This was an
application validation bug, not unsuccessful adoption or an unapplied V1.

## Fix and preserved safeguards

`DatabaseMigrationTool.validateHistory(Flyway)` is now the single validator used
by the CLI `validate` command and validation-only startup. It:

1. Reads history through a read-only connection and requires history to exist.
2. Rejects unsupported or unsuccessful baseline history, and checks that an
   actual successful version 1 `BASELINE` row exists.
3. Runs Flyway's own validation for checksums, missing/failed/pending migrations.
4. Requires states to be `SUCCESS` or `BASELINE`, except V1 `BASELINE_IGNORED`
   when both successful SQL history and the corresponding Flyway `BASELINE` info
   entry establish baseline 1. All other states are rejected with version/state
   information in the exception.

Flyway validation success alone is not sufficient. Tests also use deliberately
permissive Flyway configurations that allow pending/ignored entries; the shared
applied-state check still rejects them. Normal application/tool configuration
continues to have no ignore patterns, automatic baseline disabled, clean disabled
and out-of-order disabled. Default production startup remains validation-only.

No V1 SQL, adoption manifest, financial service, frontend or production startup
mode was changed. Existing reconciliation work was retained.

## Files changed for this fix

* `backend/backend/src/main/java/com/financetracker/backend/migration/DatabaseMigrationTool.java`
  — shared validation, successful baseline checks, CLI use.
* `backend/backend/src/main/java/com/financetracker/backend/config/DatabaseMigrationConfiguration.java`
  — delegates validation-only startup to the shared validator.
* `backend/backend/src/test/java/com/financetracker/backend/migration/MigrationHistoryValidationTests.java`
  — new disposable PostgreSQL regressions, including the actual CLI process.
* `docs/database-migrations.md` — current adopted-local status and shared validation.
* This report.

## Tests and safety verification

Fourteen new cases cover legitimate explicit V1 baseline/ignored V1; repeated
actual CLI validation with a server-enforced read-only connection; absent history;
missing baseline row; wrong baseline version/type; unsuccessful baseline; an info
result claiming baseline without genuine SQL history; pending future migration;
pending/ignored states despite permissive Flyway validation; failed history;
changed applied checksum; and missing applied migration files.

Tests compare all nine tables' synthetic records, all eight sequence counters,
catalog inventories and complete history before/after validation. Rejection never
repairs history, initializes an absent history table or executes pending SQL.
Every baseline/history corruption/test migration targets a newly created database
in `run-tests.ps1`'s disposable loopback cluster, never the existing database.

Focused migration/configuration/reconciliation suite: **75 passed**, zero
failures/errors/skips. Full backend suite: **514 passed**, zero
failures/errors/skips, retaining the 500-test baseline and adding 14 cases.
The disposable test cluster was stopped after each run. `git diff --check`
passed; changed-file credential-pattern and trailing-whitespace scans found zero
matches. V1 and its adoption manifest have no diff.

The rebuilt packaged `database.ps1 -Command validate` was also executed
successfully on the already-baselined local database with server-enforced
read-only JDBC options. Its final message was:

```text
Migration history and checksums validated. No database changes made.
```

A final read-only history query still shows the same single successful BASELINE 1
entry, no executed V1 SQL history row and no new migration entries. No genuine
pending or invalid migration needs applying to fix this issue.

The local inspector used a fixed loopback JDBC URL with
`options=-c%20default_transaction_read_only%3Don`; it called only `info` and
`validate`. History inspection used `BEGIN READ ONLY` plus connection-level
read-only enforcement. Passwords remained environment/Windows-encrypted values;
no credentials or authorization headers were logged. No existing database was
baselined again, migrated, repaired or cleaned. Neon was not contacted. Nothing
was committed, pushed or deployed.

## Command to verify the rebuilt tool

```powershell
cd C:\Users\Darsh\FinanceTracker\backend\backend
$env:DB_URL = 'jdbc:postgresql://localhost:5432/finance_tracker_db'
$env:DB_USERNAME = 'postgres'
.\database.ps1 -Command validate
```

The script packages current source first, loads the existing encrypted password,
and runs the standalone CLI without Spring, schedulers or financial services.
Expected final message:

```text
Migration history and checksums validated. No database changes made.
```

Do not rerun baseline or migrate to fix this validation error. If a future
validation failure identifies a genuinely unexpected state, investigate it rather
than adding ignore patterns or using repair/clean.
