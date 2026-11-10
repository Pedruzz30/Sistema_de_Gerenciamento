# Libera a porta do celular do atendente no firewall do Windows, só para a rede local da loja.
# Precisa de administrador: pede a permissão do Windows uma vez.
param([switch]$SemPausa)
. (Join-Path $PSScriptRoot 'comum.ps1')
$C = Get-Caminhos (Split-Path (Split-Path $PSScriptRoot))

$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) {
    $argumentos = "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`""
    Start-Process -FilePath 'powershell.exe' -ArgumentList $argumentos -Verb RunAs -Wait
    exit 0
}

try {
    $porta = Get-PortaCelular (Read-Config $C)
    $nome = 'StockOS celular do atendente'
    Get-NetFirewallRule -DisplayName $nome -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    # LocalSubnet: só aparelhos da mesma rede (o Wi-Fi da loja). Vale em rede pública ou privada,
    # porque muitos roteadores aparecem como "rede pública" no Windows.
    New-NetFirewallRule -DisplayName $nome -Direction Inbound -Protocol TCP -LocalPort $porta `
        -RemoteAddress LocalSubnet -Action Allow -Profile Any | Out-Null
    Write-Host ''
    Write-Host "Pronto: o celular pode abrir a porta $porta pelo Wi-Fi da loja." -ForegroundColor Green
    Write-Host 'O endereço e o código para o celular estão no Dashboard do StockOS.'
} catch {
    Write-Host "Não foi possível liberar: $($_.Exception.Message)" -ForegroundColor Red
    if (-not $SemPausa) { Read-Host 'Aperte Enter para fechar' | Out-Null }
    exit 1
}
if (-not $SemPausa) { Read-Host 'Aperte Enter para fechar' | Out-Null }
