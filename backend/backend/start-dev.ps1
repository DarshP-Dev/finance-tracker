$ErrorActionPreference = 'Stop'

# Keep the development signing key across restarts without committing it.
$originalJwtSecret = $env:JWT_SECRET
Push-Location $PSScriptRoot
try {
    if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
        $secretDirectory = Join-Path $PSScriptRoot '.local'
        $secretPath = Join-Path $secretDirectory 'jwt-secret'
        if (-not (Test-Path -LiteralPath $secretPath)) {
            New-Item -ItemType Directory -Path $secretDirectory -Force | Out-Null
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
    $env:JWT_SECRET = $originalJwtSecret
    Pop-Location
}
