param(
    [switch]$CheckOnly,
    [switch]$CheckJavaOnly
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$composeFile = Join-Path $root 'compose.stockos-local.yml'
$envFile = Join-Path $root '.stockos-local.env'
$projectName = 'stockos-local-legacy'

function Require-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Comando '$Name' nao encontrado. Instale-o e reinicie o terminal."
    }
}

# Fail before Docker startup if Maven is not using JDK 25+. java -version
# alone is NOT sufficient: Maven may be launched from an older JAVA_HOME.
if (-not $CheckOnly) {
    Require-Command 'mvn'
    Require-Command 'java'
    $mavenVersion = (& mvn -version 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) {
        throw 'Nao foi possivel consultar mvn -version. Confira a instalacao do Maven.'
    }
    $jdkMatch = [regex]::Match($mavenVersion, '(?im)^\s*Java version:\s*(\d+)')
    if (-not $jdkMatch.Success) {
        throw "Nao identifiquei a versao Java usada pelo Maven. Execute mvn -version e confira JAVA_HOME."
    }
    $javaMajor = [int]$jdkMatch.Groups[1].Value
    if ($javaMajor -lt 25) {
        throw ("O projeto requer JDK 25+, mas o Maven esta usando Java " + $javaMajor + ". " +
            "Instale com: winget install --id EclipseAdoptium.Temurin.25.JDK -e. " +
            "Abra novo PowerShell e confira 'mvn -version'. " +
            "Se continuar antigo, ajuste JAVA_HOME para a pasta do JDK 25 (nao a bin).")
    }
    Write-Host ("PASS: Maven esta executando sob JDK " + $javaMajor + ".")
}

if ($CheckJavaOnly) {
    # CI-only Java preflight: never creates containers, changes env or loads secrets.
    exit 0
}

Require-Command 'docker'
& docker compose version | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose nao esta disponivel.' }

if ($CheckOnly) {
    # CI syntax-only check: never starts a container or creates a secret.
    $env:STOCKOS_LOCAL_DB_PASSWORD = 'ci_config_validation_only'
    & docker compose -f $composeFile config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose invalido.' }
    Write-Host 'PASS: Docker Compose local validado (somente verificacao).'
    exit 0
}

Push-Location $root
try {
    if (-not (Test-Path -LiteralPath $envFile)) {
        [byte[]]$randomBytes = New-Object byte[] 32
        $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($randomBytes) }
        finally { $rng.Dispose() }
        $generated = [Convert]::ToBase64String($randomBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
        [System.IO.File]::WriteAllText($envFile, "STOCKOS_LOCAL_DB_PASSWORD=$generated" + [Environment]::NewLine)
        Write-Host 'Senha local gerada e salva no arquivo ignorado .stockos-local.env.'
    }

    $matchLine = Get-Content -LiteralPath $envFile | Where-Object {
        $_ -match '^STOCKOS_LOCAL_DB_PASSWORD=[A-Za-z0-9_-]{32,}$'
    } | Select-Object -First 1
    if (-not $matchLine) {
        throw '.stockos-local.env invalido. Nao apague um banco existente; verifique a senha local.'
    }
    $password = $matchLine.Substring('STOCKOS_LOCAL_DB_PASSWORD='.Length)
    $env:STOCKOS_LOCAL_DB_PASSWORD = $password

    Write-Host 'Iniciando PostgreSQL local em 127.0.0.1:55432 (Docker)...'
    & docker compose --project-name $projectName -f $composeFile up -d --wait
    if ($LASTEXITCODE -ne 0) {
        throw 'Falha ao iniciar o PostgreSQL local. Verifique se Docker Desktop esta rodando e se a porta 55432 esta livre.'
    }

    # SPRING_APPLICATION_JSON overrides any previously imported
    # application-local.properties to prevent accidentally choosing an old or
    # remote database. No DB password is put on the command line.
    $spring = @{
        server = @{ address = '127.0.0.1'; port = 8080 }
        spring = @{
            datasource = @{
                url = 'jdbc:postgresql://127.0.0.1:55432/stockos_legacy_local'
                username = 'stockos_local'
                password = $password
            }
            flyway = @{
                enabled = $true
                locations = 'classpath:db/migration'
                'baseline-on-migrate' = $false
            }
            jpa = @{ hibernate = @{ 'ddl-auto' = 'validate' } }
        }
        stockos = @{
            security = @{ mode = 'legacy-dev' }
            seed = @{ 'demo-menu' = @{ enabled = $false } }
        }
    }
    $env:SPRING_APPLICATION_JSON = $spring | ConvertTo-Json -Depth 10 -Compress
    $env:DB_URL = 'jdbc:postgresql://127.0.0.1:55432/stockos_legacy_local'
    $env:DB_USERNAME = 'stockos_local'
    $env:DB_PASSWORD = $password
    $env:STOCKOS_SERVER_ADDRESS = '127.0.0.1'
    $env:STOCKOS_SECURITY_MODE = 'legacy-dev'

    Write-Host ''
    Write-Host 'Abrindo o projeto LEGADO local com PostgreSQL separado.'
    Write-Host 'URL: http://localhost:8080/login'
    Write-Host 'Nao use esta conta local para dados reais. Para encerrar o Java, pressione Ctrl+C.'
    Write-Host ''
    & mvn '-Dspring-boot.run.main-class=app.Main' '-Dspring-boot.run.profiles=local-legacy' spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw 'Aplicacao nao iniciou. Copie o ERRO do terminal, sem senhas, para investigarmos.' }
} finally {
    Pop-Location
}
