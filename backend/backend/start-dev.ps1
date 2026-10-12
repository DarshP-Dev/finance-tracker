$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'dev-environment.ps1')
foreach ($localConnectionUrl in @($env:DB_URL, $env:SPRING_DATASOURCE_URL)) {
    if ($localConnectionUrl -and $localConnectionUrl -notmatch '^jdbc:postgresql://(localhost|127\.0\.0\.1)(:[0-9]+)?/[^?]+(\?.*)?$') {
        throw 'start-dev.ps1 is local-only. Configure production separately; see docs/database-migrations.md.'
    }
}
if ($env:DB_DDL_AUTO -and $env:DB_DDL_AUTO -ne 'validate') {
    throw 'Remove the obsolete DB_DDL_AUTO override; Hibernate now validates and Flyway owns the schema.'
}
$originalMigrationMode = $env:DB_MIGRATION_MODE
try {
    if ([string]::IsNullOrWhiteSpace($env:DB_MIGRATION_MODE)) { $env:DB_MIGRATION_MODE = 'migrate' }
    Write-Host 'Flyway will reject a populated database without verified baseline history. See docs/database-migrations.md.'
    Invoke-WithDevEnvironment {
        & .\mvnw.cmd spring-boot:run
        if ($LASTEXITCODE -ne 0) { throw 'Backend startup failed. Check Flyway history/schema diagnostics; do not enable automatic baselining.' }
    }
} finally {
    $env:DB_MIGRATION_MODE = $originalMigrationMode
}
