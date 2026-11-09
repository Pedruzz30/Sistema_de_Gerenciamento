# Instala o StockOS neste computador. Em uma instalação existente, atualiza sem tocar nos dados.
# Não precisa de administrador nem de Docker.
param(
    [string]$Destino = 'C:\StockOS',
    [switch]$SemAbrir
)
. (Join-Path $PSScriptRoot 'comum.ps1')

$Pacote = Split-Path (Split-Path $PSScriptRoot)
$Novo = Join-Path $Pacote 'programa'
$Manifesto = Get-Content -LiteralPath (Join-Path $Novo 'versao.json') -Raw -Encoding UTF8 | ConvertFrom-Json

function Test-PortaLivre([int]$Porta) {
    $ouvinte = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, $Porta)
    try { $ouvinte.Start(); $ouvinte.Stop(); return $true } catch { return $false }
}

function Get-PortaLivre([int[]]$Opcoes, [string]$Nome) {
    foreach ($porta in $Opcoes) { if (Test-PortaLivre $porta) { return $porta } }
    throw "Nenhuma porta livre para $Nome ($($Opcoes -join ', '))."
}

function New-Atalho([string]$Arquivo, [string]$Script, [string]$Descricao) {
    $shell = New-Object -ComObject WScript.Shell
    $atalho = $shell.CreateShortcut($Arquivo)
    $atalho.TargetPath = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
    $atalho.Arguments = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$Script`""
    $atalho.WorkingDirectory = Split-Path $Script
    $atalho.WindowStyle = 7
    $atalho.Description = $Descricao
    $atalho.IconLocation = (Join-Path $env:SystemRoot 'System32\shell32.dll') + ',43'
    $atalho.Save()
}

function Set-Atalhos([hashtable]$C) {
    $scripts = Join-Path $C.Programa 'scripts'
    New-Atalho (Join-Path ([Environment]::GetFolderPath('Desktop')) 'StockOS.lnk') (Join-Path $scripts 'abrir.ps1') 'Abre o StockOS'
    New-Atalho (Join-Path ([Environment]::GetFolderPath('Startup')) 'StockOS (iniciar).lnk') (Join-Path $scripts 'iniciar.ps1') 'Liga o StockOS ao entrar no Windows'
    Get-ChildItem -LiteralPath (Join-Path $C.Programa 'atalhos') -Filter '*.cmd' | Copy-Item -Destination $C.Raiz -Force
}

function Copy-Programa([hashtable]$C) {
    Copy-Item -LiteralPath $Novo -Destination $C.Programa -Recurse -Force
}

function Protect-Pasta([string]$Pasta) {
    # Só este usuário, o sistema e os administradores: a pasta guarda a senha do banco e os dados.
    $eu = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $resultado = Invoke-Programa (Join-Path $env:SystemRoot 'System32\icacls.exe') @(
        $Pasta, '/inheritance:r', '/grant:r', "*${eu}:(OI)(CI)F", '*S-1-5-18:(OI)(CI)F', '*S-1-5-32-544:(OI)(CI)F'
    )
    Assert-Programa $resultado 'Não foi possível proteger a pasta de instalação.' | Out-Null
}

function Install-Novo([hashtable]$C) {
    if ((Test-Path -LiteralPath $C.Dados) -or (Test-Path -LiteralPath $C.Programa)) {
        throw "Já existe uma instalação incompleta em $($C.Raiz). Nada foi alterado. Renomeie essa pasta e tente de novo."
    }
    Write-Host 'Preparando a pasta...'
    New-Item -ItemType Directory -Force -Path $C.Raiz | Out-Null
    Protect-Pasta $C.Raiz
    New-Item -ItemType Directory -Force -Path $C.Backups, $C.Logs, (Split-Path $C.Config) | Out-Null
    Copy-Programa $C

    $verificacao = Invoke-Programa (Join-Path $C.PgBin 'pg_ctl.exe') @('--version')
    if ($verificacao.Codigo -ne 0) {
        throw "O banco de dados não abre neste Windows. Instale o Visual C++ Redistributable (x64) da Microsoft e tente de novo.`n$($verificacao.Saida)"
    }

    Write-Host 'Criando o banco de dados...'
    $bytes = New-Object byte[] 24
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $senha = -join ($bytes | ForEach-Object { $_.ToString('x2') })
    $config = [pscustomobject]@{
        portaSistema = Get-PortaLivre @(8080, 8090, 8095) 'o sistema'
        portaBanco   = Get-PortaLivre @(5544, 5545, 5546) 'o banco'
        senhaBanco   = $senha
    }
    $arquivoSenha = Join-Path (Split-Path $C.Config) 'senha-inicial.tmp'
    Write-TextoSemBom $arquivoSenha $senha
    try {
        Assert-Programa (Invoke-Programa (Join-Path $C.PgBin 'initdb.exe') @(
            '-D', $C.Dados, '-U', 'stockos', "--pwfile=$arquivoSenha", '-E', 'UTF8',
            '--locale=C', '--locale-provider=icu', '--icu-locale=pt-BR',
            '--auth-host=scram-sha-256', '--auth-local=scram-sha-256'
        )) 'Não foi possível criar o banco de dados.' | Out-Null
    } finally {
        Remove-Item -LiteralPath $arquivoSenha -ErrorAction SilentlyContinue
    }

    $logs = $C.Logs -replace '\\', '/'
    $ajustes = @(
        '',
        '# StockOS: só este computador acessa o banco.',
        "listen_addresses = '127.0.0.1'",
        "port = $($config.portaBanco)",
        'logging_collector = on',
        "log_directory = '$logs'",
        "log_filename = 'banco-%a.log'",
        'log_truncate_on_rotation = on',
        'log_rotation_age = 1d',
        'log_rotation_size = 0',
        "lc_messages = 'C'"
    ) -join "`r`n"
    Add-Content -LiteralPath (Join-Path $C.Dados 'postgresql.conf') -Value $ajustes -Encoding Ascii

    Start-Banco $C $config
    Invoke-Sql $C $config 'CREATE DATABASE stockos' | Out-Null
    Write-TextoSemBom $C.Config ($config | ConvertTo-Json)

    Write-Host 'Ligando o sistema (pode levar até 2 minutos na primeira vez)...'
    Start-Sistema $C $config
    if (-not (Wait-Sistema $C $config)) {
        throw "O sistema não respondeu. Veja $(Join-Path $C.Logs 'stockos.log')"
    }
    $config
}

function Update-Instalado([hashtable]$C) {
    $config = Read-Config $C
    $versaoBanco = (Get-Content -LiteralPath (Join-Path $C.Dados 'PG_VERSION') -Raw).Trim()
    if ($versaoBanco -ne "$($Manifesto.postgres)") {
        throw "Este pacote usa o banco versão $($Manifesto.postgres), e a loja está na $versaoBanco. Nada foi alterado. Essa atualização precisa de outro procedimento."
    }

    Write-Host 'Guardando uma cópia dos dados antes de atualizar...'
    Start-Banco $C $config
    $copia = New-CopiaDeSeguranca $C $config 'antes-atualizacao'
    Write-Host "Cópia guardada: $copia"

    Stop-Sistema $C
    Stop-Banco $C

    $anterior = "$($C.Programa).anterior"
    if (Test-Path -LiteralPath $anterior) { Remove-Item -LiteralPath $anterior -Recurse -Force }
    Move-Item -LiteralPath $C.Programa -Destination $anterior
    Write-Host 'Copiando a versão nova...'
    Copy-Programa $C

    Start-Banco $C $config
    Start-Sistema $C $config
    if (Wait-Sistema $C $config) {
        Remove-Item -LiteralPath $anterior -Recurse -Force
        return $config
    }

    # Volta tudo como estava: a migração do banco pode ter rodado pela metade.
    Write-Host 'A versão nova não subiu. Voltando para a anterior...'
    Stop-Sistema $C
    Stop-Banco $C
    Remove-Item -LiteralPath $C.Programa -Recurse -Force
    Move-Item -LiteralPath $anterior -Destination $C.Programa
    Start-Banco $C $config
    Restore-Backup $C $config $copia
    Start-Sistema $C $config
    if (-not (Wait-Sistema $C $config)) {
        throw "A atualização falhou e o sistema não voltou sozinho. O backup de antes está em $copia"
    }
    throw 'A atualização falhou. O sistema voltou para a versão anterior, com os dados de antes de atualizar.'
}

try {
    if (-not [Environment]::Is64BitOperatingSystem) { throw 'O StockOS precisa de Windows de 64 bits.' }
    $C = Get-Caminhos $Destino
    $disco = New-Object IO.DriveInfo ([IO.Path]::GetPathRoot($Destino))
    if ($disco.AvailableFreeSpace -lt 2GB) { throw "Pouco espaço livre em $($disco.Name). São necessários pelo menos 2 GB." }

    if (Test-Path -LiteralPath $C.Config) {
        Write-Host 'Instalação encontrada: atualizando sem apagar os dados.'
        $config = Update-Instalado $C
    } else {
        $config = Install-Novo $C
    }
    Set-Atalhos $C

    $endereco = "http://localhost:$($config.portaSistema)/login.html"
    Write-Host ''
    Write-Host "StockOS pronto. Endereço: $endereco"
    Write-Host 'Use o atalho StockOS na Área de Trabalho. Ele liga sozinho quando você entra no Windows.'
    if (-not $SemAbrir) { Start-Process $endereco }
    exit 0
} catch {
    Write-Host ''
    Write-Host "NÃO DEU CERTO: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
