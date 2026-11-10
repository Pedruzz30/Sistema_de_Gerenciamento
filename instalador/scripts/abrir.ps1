# Atalho da Área de Trabalho: garante que o sistema está no ar e abre o navegador.
# Com -Caixa, abre o Edge em janela própria com impressão direta: o cupom vai para a
# impressora padrão do Windows sem perguntar. Por isso a térmica deve ser a padrão.
param([switch]$Caixa)
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
    $endereco = "http://localhost:$($config.portaSistema)/login.html"
    $edge = @("${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe", "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe") |
        Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Select-Object -First 1
    if ($Caixa -and $edge) {
        # Perfil próprio: a opção de impressão direta só vale num navegador aberto com ela.
        $perfil = Join-Path $C.Raiz 'navegador-caixa'
        Start-Process -FilePath $edge -ArgumentList @("--app=$endereco", '--kiosk-printing', "--user-data-dir=`"$perfil`"",
            '--no-first-run', '--no-default-browser-check')
    } else {
        Start-Process $endereco
    }
    exit 0
} catch {
    Show-Erro $_.Exception.Message
    exit 1
}
