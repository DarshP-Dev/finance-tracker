param(
    [Parameter(Mandatory)][ValidateSet('verify','baseline','migrate','validate')][string]$Command,
    [string]$ConfirmDatabase,
    [switch]$BackupVerified,
    [switch]$AllowRemote
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'dev-environment.ps1')
$originalToolUrl = $env:DB_URL
$originalToolUsername = $env:DB_USERNAME
try {
    if ([string]::IsNullOrWhiteSpace($env:DB_URL)) { $env:DB_URL = 'jdbc:postgresql://localhost:5432/finance_tracker_db' }
    $toolIsLocal = $env:DB_URL -match '^jdbc:postgresql://(localhost|127\.0\.0\.1)(:[0-9]+)?/'
    if (-not $toolIsLocal -and (-not $AllowRemote -or -not $env:DB_PASSWORD -or -not $env:DB_USERNAME)) {
        throw 'Remote operations require -AllowRemote and explicitly configured DB_USERNAME/DB_PASSWORD. Production also requires separate approval and a verified backup.'
    }
    if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME)) { $env:DB_USERNAME = 'postgres' }
    if ($Command -eq 'baseline' -and (-not $ConfirmDatabase -or -not $BackupVerified)) {
        throw 'Baseline requires -ConfirmDatabase <exact database name> -BackupVerified after schema review and verified backup.'
    }
    Invoke-WithDevEnvironment -InitializeJwt $false -Action {
        & .\mvnw.cmd -B -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw 'Migration tool build failed; no database command executed.' }
        $toolArgs = @($Command)
        if ($Command -eq 'baseline') { $toolArgs += @($ConfirmDatabase, 'backup-verified') }
        & java '-Dloader.main=com.financetracker.backend.migration.DatabaseMigrationTool' -cp target/backend-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher @toolArgs
        if ($LASTEXITCODE -ne 0) { throw 'Database command failed. Review differences/history; never use repair or automatic baseline to bypass the error.' }
    }
} finally {
    $env:DB_URL = $originalToolUrl
    $env:DB_USERNAME = $originalToolUsername
}
