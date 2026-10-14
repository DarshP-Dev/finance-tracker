# Recovery checklist after completed Flyway V1 adoption

V1 adoption and compatible backend deployment are complete. This checklist is
for a future separately approved recovery or schema release; it is not permission
to restore, reconcile, baseline, migrate, alter accounts, or change production
settings. The [milestone report](flyway-production-adoption-report.md) records the
completed recovery verification and production preservation checkpoints.

## Retain usable private evidence

- Keep the restore-tested pre-adoption recovery point and its source/time recorded
  privately. Availability and retention must be rechecked before future use.
- Keep backups, encrypted credential files, schema exports, catalog inventories,
  row hashes, sequence states, and raw logs under Git-ignored `.local/` storage.
  Do not publish even a schema-only export containing private owners or grants.
- The tested snapshot predates normal application writes after adoption. Account
  for those later writes when selecting a recovery point; do not claim the older
  snapshot is current or that its pre-adoption schema contains Flyway history.
- If snapshot recovery is unavailable, a full `pg_dump` with an isolated restore
  test is an alternative. A schema-only export or archive listing is not a tested
  financial-data backup. Keep all outputs private.

## Inspect before acting

1. Confirm the intended project, branch, database, and endpoint through the control
   plane privately. PostgreSQL SQL alone cannot identify a cloned Neon branch.
2. Record the incident, latest application commit, configuration, recovery point,
   and scope of approval. Use existing secure credentials; do not introduce new
   accounts or a role fence without a concrete need and separate review.
3. Inspect migration history, schema differences, and relevant application errors
   read-only. Investigate an uncertain operation's transaction outcome before
   retrying it. Baseline is not a generic repair command.
4. Prefer a reviewed compatible application fix when records and schema are intact.
   An application rollback does not undo database history or restore lost records.
   Do not return to the backend that rejects a genuine BASELINE 1, or a legacy
   artifact that uses Hibernate `update`.

## Recover into an isolated target

1. Obtain approval for the exact new restore target and recovery timestamp. Use
   an isolated branch/target, not a one-step replacement of production. Avoid
   production switch-over or finalization until the restored state is verified.
2. Keep restored targets detached from application writers and scheduled jobs.
   Use a separate secure credential context and verified TLS. Do not reuse an old
   production-pinned reconciliation script against a new target.
3. Compare recovered schema, ownership/permission metadata, all nine table counts
   and full-row hashes, and all eight sequence values/`is_called` states with the
   appropriate dated reference. Keep actual records and digests private. Explain
   differences attributable to snapshot age; do not silently modify invalid data.
4. Inspect the restored history. A pre-adoption snapshot can legitimately lack
   both positivity checks and Flyway history. Any reconciliation or explicit
   adoption of that restored target requires fresh review and separate approval.
   Do not edit V1 or its manifest to conceal differences.
5. Test the compatible backend against the isolated target with providers and
   scheduling disabled. Confirm strict Flyway validation, Hibernate validation,
   and representative application behavior before approving connection switch-over.

## Release and resume deliberately

During an approved maintenance window, stop scheduled jobs and application writers,
check sessions, and hold automatic deployment until schema compatibility is ready.
Pause controls alone do not establish that all retained/Preview deployments or
background connections have stopped. Account for known clients and the agreed
beta risk; do not terminate unidentified sessions automatically.

The protected deployment rehearsal established that a paused Vercel project can
block a new build. If temporary resumption is needed, verify protection over the
production domain first and use only the reviewed compatible source. Restore
approved maintenance controls on failures. Do not expose an incompatible old
backend while waiting for its replacement.

Keep production startup validation-only. Future V2+ schema migrations require a
separate controlled release, a usable recovery point, isolated tests, and explicit
approval before compatible application deployment. Do not repeat V1 baseline or
reconciliation on the current adopted database, use Flyway repair/clean to hide
failures, or modify financial records merely to make validation pass.

Before resuming service or recurring processing, obtain the intended approval,
confirm the current compatible deployment, review startup and preservation results,
and perform authorized smoke checks. Keep the Spring scheduler and Vercel cron
as distinct controls; select the intended runner deliberately. Existing recurring
processing supports due-date catch-up and deduplication, subject to its configured
per-definition cap; do not force unreviewed catch-up or enable two runners by default.
