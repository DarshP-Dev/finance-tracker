$ErrorActionPreference = 'Stop'

$originalDatabasePassword = $env:DB_PASSWORD
$originalJwtSecret = $env:JWT_SECRET
Push-Location $PSScriptRoot
try {
    $secretDirectory = Join-Path $PSScriptRoot '.local'
    New-Item -ItemType Directory -Path $secretDirectory -Force | Out-Null

    if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
        $databasePasswordPath = Join-Path $secretDirectory 'database-password.clixml'

        if (Test-Path -LiteralPath $databasePasswordPath) {
            $secureDatabasePassword = Import-Clixml -LiteralPath $databasePasswordPath
        } else {
            Write-Host 'Database password is not configured. Enter it once to store it with Windows user encryption.'
            $secureDatabasePassword = Read-Host 'PostgreSQL password' -AsSecureString
            $secureDatabasePassword | Export-Clixml -LiteralPath $databasePasswordPath
        }

        $databaseCredential = [System.Management.Automation.PSCredential]::new(
            'database',
            $secureDatabasePassword
        )
        $env:DB_PASSWORD = $databaseCredential.GetNetworkCredential().Password
    }

    if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
        $secretPath = Join-Path $secretDirectory 'jwt-secret'
        if (-not (Test-Path -LiteralPath $secretPath)) {
            $secretBytes = New-Object byte[] 48
            $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
            try {
                $random.GetBytes($secretBytes)
            } finally {
                $random.Dispose()
            }
            [System.IO.File]::WriteAllText($secretPath, [Convert]::ToBase64String($secretBytes))
        }
        $env:JWT_SECRET = [System.IO.File]::ReadAllText($secretPath).Trim()
    }

    & .\mvnw.cmd spring-boot:run
} finally {
    $env:DB_PASSWORD = $originalDatabasePassword
    $env:JWT_SECRET = $originalJwtSecret
    Pop-Location
}
