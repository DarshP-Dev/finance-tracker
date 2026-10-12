param(
    [string]$PostgresBin = $env:POSTGRES_BIN,
    [string]$Tests
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($PostgresBin)) {
    $PostgresBin = 'C:\Program Files\PostgreSQL\17\bin'
}
foreach ($tool in @('initdb.exe', 'pg_ctl.exe', 'createdb.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $PostgresBin $tool))) {
        throw "PostgreSQL tools missing. Set POSTGRES_BIN or pass -PostgresBin. Docker is not required."
    }
}
$testRunId = [Guid]::NewGuid().ToString('N')
$testRoot = Join-Path $PSScriptRoot ".local\flyway-tests-$testRunId"
$testData = Join-Path $testRoot 'data'
$testLog = Join-Path $testRoot 'postgres.log'
New-Item -ItemType Directory -Path $testRoot | Out-Null
$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
$listener.Start()
$testPort = $listener.LocalEndpoint.Port
$listener.Stop()
$testDatabase = "ft_test_$testRunId"
$savedTestEnvironment = @{}
foreach ($name in @('TEST_DB_URL', 'TEST_DB_USERNAME', 'TEST_DB_PASSWORD', 'PGPASSWORD', 'PGOPTIONS',
    'AI_INSIGHTS_ENABLED', 'AI_INSIGHTS_API_KEY', 'GEMINI_API_KEY',
    'MARKET_DATA_ENABLED', 'MARKET_DATA_API_KEY', 'TWELVE_DATA_API_KEY')) {
    $savedTestEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
$clusterStarted = $false
Push-Location $PSScriptRoot
try {
    # These paths are always new and belong to this invocation. Never use the installed cluster.
    $env:PGPASSWORD = $null
    $env:PGOPTIONS = $null
    $env:AI_INSIGHTS_ENABLED = 'false'
    $env:AI_INSIGHTS_API_KEY = $null
    $env:GEMINI_API_KEY = $null
    $env:MARKET_DATA_ENABLED = 'false'
    $env:MARKET_DATA_API_KEY = $null
    $env:TWELVE_DATA_API_KEY = $null
    & (Join-Path $PostgresBin 'initdb.exe') -D $testData -U postgres -A trust --encoding=UTF8 --locale=C > (Join-Path $testRoot 'initdb.log')
    if ($LASTEXITCODE -ne 0) { throw 'Disposable initdb failed.' }
    $testStart = Start-Process -FilePath (Join-Path $PostgresBin 'pg_ctl.exe') -WindowStyle Hidden -PassThru `
        -ArgumentList @('-D', "`"$testData`"", '-l', "`"$testLog`"", '-o', "`"-h 127.0.0.1 -p $testPort`"", '-w', 'start') `
        -RedirectStandardOutput (Join-Path $testRoot 'start.log') -RedirectStandardError (Join-Path $testRoot 'start-error.log')
    # Start-Process -Wait also waits for descendant postgres.exe on Windows.
    # Wait only for pg_ctl, which exits once the server is ready.
    $testStart.WaitForExit()
    if ($testStart.ExitCode -ne 0) { throw 'Disposable PostgreSQL startup failed.' }
    $clusterStarted = $true
    & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p $testPort -U postgres $testDatabase
    if ($LASTEXITCODE -ne 0) { throw 'Disposable database creation failed.' }
    $env:TEST_DB_URL = "jdbc:postgresql://127.0.0.1:$testPort/$testDatabase"
    $env:TEST_DB_USERNAME = 'postgres'
    $env:TEST_DB_PASSWORD = ''
    Write-Host "Running against disposable PostgreSQL only (port $testPort). Logs: $testRoot"
    $mavenArgs = @('-B', 'test')
    if ($Tests) { $mavenArgs += "-Dtest=$Tests" }
    & .\mvnw.cmd @mavenArgs
    if ($LASTEXITCODE -ne 0) { throw 'Backend tests failed; inspect target/surefire-reports.' }
} finally {
    if ($clusterStarted) {
        & (Join-Path $PostgresBin 'pg_ctl.exe') -D $testData -m fast -w stop
    }
    foreach ($name in $savedTestEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $savedTestEnvironment[$name], 'Process')
    }
    Pop-Location
    # Keep stopped, ignored test data/logs for diagnosis. No recursive deletion.
}
