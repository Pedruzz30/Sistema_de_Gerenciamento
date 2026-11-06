# Liga o banco e o sistema. Roda ao entrar no Windows; se já estiverem no ar, não faz nada.
. (Join-Path $PSScriptRoot 'comum.ps1')
$C = Get-Caminhos (Split-Path (Split-Path $PSScriptRoot))
try {
    $config = Read-Config $C
    Start-Banco $C $config
    Start-Sistema $C $config
    exit 0
} catch {
    $linha = "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $($_.Exception.Message)`r`n"
    [IO.File]::AppendAllText((Join-Path $C.Logs 'inicio.log'), $linha)
    exit 1
}
