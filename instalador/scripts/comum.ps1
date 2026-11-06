# Funções usadas pela instalação, pelo início do sistema e pela restauração.
# Precisa rodar no PowerShell 5.1, o que já vem no Windows.
$ErrorActionPreference = 'Stop'

function Get-Caminhos([string]$Raiz) {
    @{
        Raiz     = $Raiz
        Programa = Join-Path $Raiz 'programa'
        PgBin    = Join-Path $Raiz 'programa\pgsql\bin'
        Javaw    = Join-Path $Raiz 'programa\java\bin\javaw.exe'
        Jar      = Join-Path $Raiz 'programa\stockos.jar'
        Dados    = Join-Path $Raiz 'dados'
        Backups  = Join-Path $Raiz 'backups'
        Logs     = Join-Path $Raiz 'logs'
        Config   = Join-Path $Raiz 'config\stockos.json'
        Pid      = Join-Path $Raiz 'config\sistema.pid'
    }
}

function Read-Config([hashtable]$C) {
    if (-not (Test-Path -LiteralPath $C.Config)) { throw "StockOS não está instalado em $($C.Raiz)." }
    Get-Content -LiteralPath $C.Config -Raw -Encoding UTF8 | ConvertFrom-Json
}

function Write-TextoSemBom([string]$Caminho, [string]$Texto) {
    [IO.File]::WriteAllText($Caminho, $Texto, (New-Object Text.UTF8Encoding($false)))
}

# Roda um programa e devolve código e saída, sem deixar o PowerShell tratar stderr como erro.
function Invoke-Programa([string]$Exe, [string[]]$Argumentos) {
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $Exe
    $info.Arguments = ($Argumentos | ForEach-Object { '"' + ($_ -replace '"', '\"') + '"' }) -join ' '
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $p = [Diagnostics.Process]::Start($info)
    $saida = $p.StandardOutput.ReadToEndAsync()
    $erro = $p.StandardError.ReadToEndAsync()
    $p.WaitForExit()
    [pscustomobject]@{ Codigo = $p.ExitCode; Saida = ($saida.Result + $erro.Result) }
}

function Assert-Programa($Resultado, [string]$Mensagem) {
    if ($Resultado.Codigo -ne 0) { throw "$Mensagem`n$($Resultado.Saida)" }
    $Resultado
}

function Set-SenhaBanco($Config) { $env:PGPASSWORD = $Config.senhaBanco }

function Test-Banco([hashtable]$C, $Config) {
    (Invoke-Programa (Join-Path $C.PgBin 'pg_isready.exe') @('-h', '127.0.0.1', '-p', "$($Config.portaBanco)", '-q')).Codigo -eq 0
}

function Start-Banco([hashtable]$C, $Config) {
    if (Test-Banco $C $Config) { return }
    $log = Join-Path $C.Logs 'banco-inicio.log'
    # Sem esperar o pg_ctl: o banco herda as saídas dele, e a espera nunca terminaria.
    $argumentos = "-D `"$($C.Dados)`" -l `"$log`" -W start"
    Start-Process -FilePath (Join-Path $C.PgBin 'pg_ctl.exe') -ArgumentList $argumentos -WindowStyle Hidden | Out-Null
    # Depois de queda de energia, o banco se recupera sozinho antes de aceitar conexões.
    $limite = (Get-Date).AddSeconds(180)
    while ((Get-Date) -lt $limite) {
        if (Test-Banco $C $Config) { return }
        Start-Sleep -Seconds 1
    }
    throw "O banco de dados não iniciou. Veja $log"
}

function Stop-Banco([hashtable]$C) {
    if (-not (Test-Path -LiteralPath (Join-Path $C.Dados 'postmaster.pid'))) { return }
    Invoke-Programa (Join-Path $C.PgBin 'pg_ctl.exe') @('-D', $C.Dados, '-m', 'fast', '-w', '-t', '60', 'stop') | Out-Null
}

function Get-ProcessoSistema([hashtable]$C) {
    if (-not (Test-Path -LiteralPath $C.Pid)) { return $null }
    $numero = (Get-Content -LiteralPath $C.Pid -Raw).Trim()
    $processo = Get-Process -Id $numero -ErrorAction SilentlyContinue
    if ($processo -and $processo.ProcessName -eq 'javaw') { return $processo }
    return $null
}

function Start-Sistema([hashtable]$C, $Config) {
    if (Get-ProcessoSistema $C) { return }
    $env:DB_URL = "jdbc:postgresql://127.0.0.1:$($Config.portaBanco)/stockos"
    $env:DB_USERNAME = 'stockos'
    $env:DB_PASSWORD = $Config.senhaBanco
    $argumentos = @(
        '-Xms128m', '-Xmx512m', '-jar', "`"$($C.Jar)`"",
        '--server.address=127.0.0.1',
        "--server.port=$($Config.portaSistema)",
        '--spring.jpa.show-sql=false',
        '--spring.flyway.baseline-on-migrate=false',
        '--stockos.seed.demo-catalog.enabled=false',
        "--stockos.backup.pasta=`"$($C.Backups)`"",
        "--stockos.backup.pg-bin=`"$($C.PgBin)`"",
        "--logging.file.name=`"$(Join-Path $C.Logs 'stockos.log')`"",
        '--logging.logback.rollingpolicy.max-history=14'
    ) -join ' '
    $processo = Start-Process -FilePath $C.Javaw -ArgumentList $argumentos -WorkingDirectory $C.Logs -PassThru
    Remove-Item Env:DB_PASSWORD
    Write-TextoSemBom $C.Pid "$($processo.Id)"
}

function Stop-Sistema([hashtable]$C) {
    $processo = Get-ProcessoSistema $C
    if ($processo) {
        Stop-Process -Id $processo.Id -Force
        $processo.WaitForExit(30000) | Out-Null
    }
    Remove-Item -LiteralPath $C.Pid -ErrorAction SilentlyContinue
}

function Wait-Sistema([hashtable]$C, $Config, [int]$Segundos = 240) {
    $limite = (Get-Date).AddSeconds($Segundos)
    while ((Get-Date) -lt $limite) {
        if (-not (Get-ProcessoSistema $C)) { return $false }
        try {
            $resposta = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 -Uri "http://127.0.0.1:$($Config.portaSistema)/api/auth/config"
            if ($resposta.StatusCode -eq 200) { return $true }
        } catch {
            Start-Sleep -Seconds 1
        }
    }
    return $false
}

function Test-ArquivoBackup([hashtable]$C, [string]$Arquivo) {
    if (-not (Test-Path -LiteralPath $Arquivo -PathType Leaf)) { return $false }
    (Invoke-Programa (Join-Path $C.PgBin 'pg_restore.exe') @('--list', $Arquivo)).Codigo -eq 0
}

# Cópia avulsa antes de uma operação arriscada. O nome não segue o padrão do backup
# automático, então a limpeza dele nunca apaga estes arquivos.
function New-CopiaDeSeguranca([hashtable]$C, $Config, [string]$Motivo) {
    New-Item -ItemType Directory -Force -Path $C.Backups | Out-Null
    $arquivo = Join-Path $C.Backups ("stockos-$Motivo-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.dump')
    Set-SenhaBanco $Config
    Assert-Programa (Invoke-Programa (Join-Path $C.PgBin 'pg_dump.exe') @(
        '-h', '127.0.0.1', '-p', "$($Config.portaBanco)", '-U', 'stockos', '-d', 'stockos', '-w', '-Fc', '--file', $arquivo
    )) 'Não foi possível copiar o banco antes de continuar.' | Out-Null
    if (-not (Test-ArquivoBackup $C $arquivo)) { throw "A cópia de segurança $arquivo não passou na conferência." }
    # Guarda as 5 mais novas de cada motivo.
    Get-ChildItem -LiteralPath $C.Backups -Filter "stockos-$Motivo-*.dump" |
        Sort-Object Name -Descending | Select-Object -Skip 5 | Remove-Item -Force
    $arquivo
}

function Invoke-Sql([hashtable]$C, $Config, [string]$Sql) {
    Set-SenhaBanco $Config
    Assert-Programa (Invoke-Programa (Join-Path $C.PgBin 'psql.exe') @(
        '-h', '127.0.0.1', '-p', "$($Config.portaBanco)", '-U', 'stockos', '-d', 'postgres', '-w', '-v', 'ON_ERROR_STOP=1', '-tA', '-c', $Sql
    )) 'Erro ao falar com o banco de dados.'
}

function Disconnect-Banco([hashtable]$C, $Config, [string]$Nome) {
    Invoke-Sql $C $Config "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$Nome' AND pid <> pg_backend_pid()" | Out-Null
}

# Carrega o backup em um banco novo e só troca no fim: se falhar no meio, nada muda.
# O sistema precisa estar parado. O banco anterior fica guardado como stockos_anterior.
function Restore-Backup([hashtable]$C, $Config, [string]$Arquivo) {
    Invoke-Sql $C $Config 'DROP DATABASE IF EXISTS stockos_novo' | Out-Null
    Invoke-Sql $C $Config 'CREATE DATABASE stockos_novo' | Out-Null
    Set-SenhaBanco $Config
    $restauracao = Invoke-Programa (Join-Path $C.PgBin 'pg_restore.exe') @(
        '-h', '127.0.0.1', '-p', "$($Config.portaBanco)", '-U', 'stockos', '-d', 'stockos_novo', '-w', '--no-owner', '--exit-on-error', $Arquivo
    )
    if ($restauracao.Codigo -ne 0) {
        Invoke-Sql $C $Config 'DROP DATABASE IF EXISTS stockos_novo' | Out-Null
        throw "O backup não pôde ser carregado. Nada foi alterado.`n$($restauracao.Saida)"
    }
    Disconnect-Banco $C $Config 'stockos'
    Invoke-Sql $C $Config 'DROP DATABASE IF EXISTS stockos_anterior' | Out-Null
    Invoke-Sql $C $Config 'ALTER DATABASE stockos RENAME TO stockos_anterior' | Out-Null
    Invoke-Sql $C $Config 'ALTER DATABASE stockos_novo RENAME TO stockos' | Out-Null
}

# Volta ao banco de antes de Restore-Backup, quando o sistema não sobe com o backup.
function Undo-Restauracao([hashtable]$C, $Config) {
    Disconnect-Banco $C $Config 'stockos'
    Invoke-Sql $C $Config 'DROP DATABASE IF EXISTS stockos_recusado' | Out-Null
    Invoke-Sql $C $Config 'ALTER DATABASE stockos RENAME TO stockos_recusado' | Out-Null
    Invoke-Sql $C $Config 'ALTER DATABASE stockos_anterior RENAME TO stockos' | Out-Null
    Invoke-Sql $C $Config 'DROP DATABASE stockos_recusado' | Out-Null
}
