# Atalho da Área de Trabalho: garante que o sistema está no ar e abre o navegador.
. (Join-Path $PSScriptRoot 'comum.ps1')
$C = Get-Caminhos (Split-Path (Split-Path $PSScriptRoot))

function Show-Erro([string]$Mensagem) {
    Add-Type -AssemblyName System.Windows.Forms
    [Windows.Forms.MessageBox]::Show($Mensagem, 'StockOS', 'OK', 'Error') | Out-Null
}

try {
    $config = Read-Config $C
    Start-Banco $C $config
    Start-Sistema $C $config
    if (-not (Wait-Sistema $C $config)) {
        throw "O sistema não respondeu. Veja o arquivo $(Join-Path $C.Logs 'stockos.log')"
    }
    Start-Process "http://localhost:$($config.portaSistema)/login.html"
    exit 0
} catch {
    Show-Erro $_.Exception.Message
    exit 1
}
