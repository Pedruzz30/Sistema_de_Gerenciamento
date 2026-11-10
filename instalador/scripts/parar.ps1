# Desliga o sistema e o banco, sem perder dados.
. (Join-Path $PSScriptRoot 'comum.ps1')
$C = Get-Caminhos (Split-Path (Split-Path $PSScriptRoot))
try {
    Stop-Sistema $C
    Stop-Banco $C
    exit 0
} catch {
    Write-Host "NÃO DEU CERTO: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
