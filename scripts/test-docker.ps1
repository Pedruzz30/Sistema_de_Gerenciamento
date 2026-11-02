# Runs Maven tests only against the disposable stockos_test database on local
# Docker PostgreSQL. This script never uses DB_URL or Supabase credentials.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$keys = @("STOCKOS_TEST_DB_URL", "STOCKOS_TEST_DB_USERNAME", "STOCKOS_TEST_DB_PASSWORD")
$old = @{}
foreach ($key in $keys) {
    $old[$key] = [Environment]::GetEnvironmentVariable($key, "Process")
}

Push-Location $root
try {
    & docker compose up -d --wait db
    if ($LASTEXITCODE -ne 0) { throw "Docker PostgreSQL nao iniciou." }

    $binding = (& docker compose port db 5432 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $binding -notmatch '^127\.0\.0\.1:(\d+)$') {
        throw "Porta do banco local nao encontrada: $binding"
    }
    $localPort = $Matches[1]

    # Read credentials from the running local container without printing them.
    $password = (& docker compose exec -T db printenv POSTGRES_PASSWORD | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($password)) {
        throw "Senha do container PostgreSQL nao encontrada."
    }

    $env:STOCKOS_TEST_DB_URL = "jdbc:postgresql://127.0.0.1:$localPort/stockos_test"
    $env:STOCKOS_TEST_DB_USERNAME = "stockos_local"
    $env:STOCKOS_TEST_DB_PASSWORD = $password

    Write-Host "Executando testes apenas em PostgreSQL Docker local (stockos_test)."
    & mvn -B test
    if ($LASTEXITCODE -ne 0) { throw "Testes Maven falharam (exit code: $LASTEXITCODE)." }
}
finally {
    foreach ($key in $keys) {
        [Environment]::SetEnvironmentVariable($key, $old[$key], "Process")
    }
    Pop-Location
}
