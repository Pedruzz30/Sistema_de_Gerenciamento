# Volta os dados da loja para um backup. Serve também para um notebook novo: instale o StockOS
# e restaure a partir do pen drive. Antes de trocar, guarda uma cópia do que há agora.
param(
    [string]$Arquivo,
    [switch]$Confirmado
)
. (Join-Path $PSScriptRoot 'comum.ps1')

$C = Get-Caminhos (Split-Path (Split-Path $PSScriptRoot))

function Get-BackupsDisponiveis {
    $pastas = @($C.Backups)
    foreach ($disco in [IO.DriveInfo]::GetDrives()) {
        if ($disco.IsReady) { $pastas += Join-Path $disco.RootDirectory.FullName 'StockOS-Backups' }
    }
    $pastas | Where-Object { Test-Path -LiteralPath $_ } | ForEach-Object {
        Get-ChildItem -LiteralPath $_ -Filter 'stockos-*.dump'
    } | Sort-Object LastWriteTime -Descending | Select-Object -First 15
}

function Select-Backup {
    $lista = @(Get-BackupsDisponiveis)
    Write-Host ''
    for ($i = 0; $i -lt $lista.Count; $i++) {
        Write-Host ("{0,3}. {1:dd/MM/yyyy HH:mm}  {2}" -f ($i + 1), $lista[$i].LastWriteTime, $lista[$i].FullName)
    }
    Write-Host ''
    $resposta = Read-Host 'Digite o número do backup, ou o caminho completo do arquivo'
    if ($resposta -match '^\d+$' -and [int]$resposta -ge 1 -and [int]$resposta -le $lista.Count) {
        return $lista[[int]$resposta - 1].FullName
    }
    $resposta.Trim('"', ' ')
}

try {
    $config = Read-Config $C
    Start-Banco $C $config

    if (-not $Arquivo) { $Arquivo = Select-Backup }
    if (-not (Test-ArquivoBackup $C $Arquivo)) {
        throw "O arquivo não é um backup válido do StockOS: $Arquivo. Nada foi alterado."
    }

    if (-not $Confirmado) {
        Write-Host ''
        Write-Host "Os dados de agora serão trocados pelos do backup:`n  $Arquivo" -ForegroundColor Yellow
        Write-Host 'Vendas e cadastros feitos depois desse backup saem do sistema. Uma cópia de agora fica guardada antes.'
        if ((Read-Host 'Para continuar, digite RESTAURAR') -cne 'RESTAURAR') {
            Write-Host 'Cancelado. Nada foi alterado.'
            exit 0
        }
    }

    Write-Host 'Guardando uma cópia de agora...'
    $copia = New-CopiaDeSeguranca $C $config 'antes-restauracao'
    Write-Host "Cópia guardada: $copia"

    Write-Host 'Parando o sistema e carregando o backup...'
    Stop-Sistema $C
    try {
        Restore-Backup $C $config $Arquivo
    } catch {
        Start-Sistema $C $config
        Wait-Sistema $C $config | Out-Null
        throw
    }

    Write-Host 'Ligando o sistema...'
    Start-Sistema $C $config
    if (-not (Wait-Sistema $C $config)) {
        Stop-Sistema $C
        Undo-Restauracao $C $config
        Start-Sistema $C $config
        Wait-Sistema $C $config | Out-Null
        throw 'O sistema não ligou com esse backup. Os dados de antes foram recolocados.'
    }
    Write-Host ''
    Write-Host 'Pronto: os dados do backup estão no sistema.' -ForegroundColor Green
    exit 0
} catch {
    Write-Host ''
    Write-Host "NÃO DEU CERTO: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
